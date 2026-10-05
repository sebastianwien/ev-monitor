package com.evmonitor.application;

import com.evmonitor.application.StripeReportService.TrialFunnel;
import com.evmonitor.application.WeeklyReportStats.Metric;
import com.evmonitor.application.WeeklyReportStats.Trend;
import com.evmonitor.infrastructure.email.EmailTemplateRenderer;
import com.evmonitor.infrastructure.persistence.AdminQueryRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Interner Wochenreport an die Gruender: Neuzugaenge der letzten 7 Tage (Nutzer, Trials, Autos,
 * Ladevorgaenge, Fahrten) mit Vergleich zur Vorwoche. Empfaenger kommen per Env-Var
 * (WEEKLY_REPORT_RECIPIENTS); leer = Feature aus. Fire-and-forget: Fehler werden geloggt, nie geworfen.
 */
@Service
@Slf4j
public class WeeklyReportService {

    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Duration WEEK = Duration.ofDays(7);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AdminQueryRepository adminQueryRepository;
    private final StripeReportService stripeReportService;
    private final JavaMailSender mailSender;
    private final Clock clock;

    @Value("${app.mail.from:noreply@ev-monitor.net}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Value("${app.report.weekly-recipients:}")
    private String recipients;

    @Autowired
    public WeeklyReportService(AdminQueryRepository adminQueryRepository,
                               StripeReportService stripeReportService,
                               JavaMailSender mailSender) {
        this(adminQueryRepository, stripeReportService, mailSender, Clock.system(ZONE));
    }

    WeeklyReportService(AdminQueryRepository adminQueryRepository, StripeReportService stripeReportService,
                        JavaMailSender mailSender, Clock clock) {
        this.adminQueryRepository = adminQueryRepository;
        this.stripeReportService = stripeReportService;
        this.mailSender = mailSender;
        this.clock = clock;
    }

    /** Baut und verschickt den Report an die konfigurierten Empfaenger. Ohne Empfaenger: no-op. */
    public void send() {
        String[] to = parseRecipients(recipients);
        if (to.length == 0) {
            log.debug("Wochenreport: keine Empfaenger konfiguriert, ueberspringe");
            return;
        }
        try {
            WeeklyReportStats stats = buildStats();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subjectFor(stats));
            helper.setText(render(stats), true);
            mailSender.send(message);
            log.info("Wochenreport {} gesendet an {} Empfaenger", stats.weekLabel(), to.length);
        } catch (Exception e) {
            log.error("Wochenreport konnte nicht gesendet werden", e);
        }
    }

    WeeklyReportStats buildStats() {
        Instant end = clock.instant();
        Instant start = end.minus(WEEK);
        Instant prevStart = start.minus(WEEK);

        TrialFunnel current = stripeReportService.trialFunnel(start, end);
        TrialFunnel previous = stripeReportService.trialFunnel(prevStart, start);

        ZonedDateTime endZoned = end.atZone(ZONE);
        String weekLabel = "KW " + endZoned.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String periodLabel = start.atZone(ZONE).format(DAY_MONTH) + " bis " + endZoned.format(DAY_MONTH_YEAR);

        return new WeeklyReportStats(
                weekLabel,
                periodLabel,
                dbMetric(adminQueryRepository::countNewUsers, start, end, prevStart),
                new Metric(current.started(), previous.started()),
                new Metric(current.ended(), previous.ended()),
                new Metric(current.converted(), previous.converted()),
                dbMetric(adminQueryRepository::countNewCars, start, end, prevStart),
                dbMetric(adminQueryRepository::countNewEvLogs, start, end, prevStart),
                dbMetric(adminQueryRepository::countNewTrips, start, end, prevStart),
                current.configured());
    }

    String subjectFor(WeeklyReportStats s) {
        return "Wochenreport " + s.weekLabel() + ": +" + s.newUsers().current() + " Nutzer, +"
                + s.trialsStarted().current() + " Trials, +" + s.newEvLogs().current() + " Ladevorgänge";
    }

    String render(WeeklyReportStats s) {
        Map<String, String> vars = new HashMap<>();
        vars.put("weekLabel", s.weekLabel());
        vars.put("periodLabel", s.periodLabel());
        vars.put("adminUrl", baseUrl + "/admin");
        putMetric(vars, "users", s.newUsers());
        putMetric(vars, "cars", s.newCars());
        putMetric(vars, "logs", s.newEvLogs());
        putMetric(vars, "trips", s.newTrips());
        putMetric(vars, "trialsStarted", s.trialsStarted());
        putMetric(vars, "trialsEnded", s.trialsEnded());
        putMetric(vars, "trialsConverted", s.trialsConverted());
        vars.put("conversionRate", s.conversionRateLabel());
        vars.put("trialsNote", s.trialsAvailable()
                ? "Quelle: Stripe. Beendet = Trial-Ende lag in dieser Woche, umgewandelt = nicht vorher gekündigt."
                : "Stripe nicht konfiguriert, Trial-Zahlen fehlen.");
        return EmailTemplateRenderer.render("weekly-report.html", "de", vars);
    }

    private static void putMetric(Map<String, String> vars, String key, Metric m) {
        vars.put(key, String.valueOf(m.current()));
        vars.put(key + "Delta", deltaLabel(m));
        vars.put(key + "Color", trendColor(m.trend()));
    }

    /** z. B. "▲ 4 zur Vorwoche", "▼ 33 zur Vorwoche", "± 0 zur Vorwoche". */
    private static String deltaLabel(Metric m) {
        return switch (m.trend()) {
            case UP -> "▲ " + m.delta() + " zur Vorwoche";
            case DOWN -> "▼ " + Math.abs(m.delta()) + " zur Vorwoche";
            case FLAT -> "± 0 zur Vorwoche";
        };
    }

    private static String trendColor(Trend t) {
        return switch (t) {
            case UP -> "#059669";
            case DOWN -> "#dc2626";
            case FLAT -> "#9ca3af";
        };
    }

    private interface Counter {
        long count(LocalDateTime from, LocalDateTime to);
    }

    /** DB-Spalten sind TIMESTAMP ohne Zone und werden mit LocalDateTime.now() der JVM geschrieben. */
    private static Metric dbMetric(Counter counter, Instant start, Instant end, Instant prevStart) {
        LocalDateTime s = toDbTime(start);
        LocalDateTime e = toDbTime(end);
        LocalDateTime p = toDbTime(prevStart);
        return new Metric(counter.count(s, e), counter.count(p, s));
    }

    private static LocalDateTime toDbTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static String[] parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .toArray(String[]::new);
    }
}
