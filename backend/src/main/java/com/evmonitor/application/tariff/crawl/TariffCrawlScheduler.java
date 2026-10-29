package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.AdminAlertService;
import com.evmonitor.application.JobLockService;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Sonntags 23:40 alle aktiven Quellen, unter Lock (bei Blue/Green laufen kurz zwei Instanzen).
 * Höchstens eine Mail je Lauf, nur wenn ein Admin etwas entscheiden muss oder eine Quelle zweimal
 * in Folge nicht lesbar war.
 */
@Component
@Slf4j
public class TariffCrawlScheduler {

    static final String LOCK_NAME = "tariff_crawl";
    static final Duration LOCK_TTL = Duration.ofHours(1);

    private final TariffCrawlService service;
    private final JobLockService locks;
    private final AdminAlertService alerts;
    private final TariffCrawlProperties props;
    private final Executor executor;

    @Autowired
    public TariffCrawlScheduler(TariffCrawlService service, JobLockService locks, AdminAlertService alerts, TariffCrawlProperties props) {
        this(service, locks, alerts, props, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "tariff-crawl-manual");
            t.setDaemon(true);
            return t;
        }));
    }

    TariffCrawlScheduler(TariffCrawlService service, JobLockService locks, AdminAlertService alerts,
                         TariffCrawlProperties props, Executor executor) {
        this.service = service;
        this.locks = locks;
        this.alerts = alerts;
        this.props = props;
        this.executor = executor;
    }

    /**
     * Alle aktiven Quellen, nicht nur fällige: ein Lauf per Knopf unter der Woche soll den Sonntag nicht
     * verschieben. Unveränderte Seiten kosten trotzdem keinen Modellaufruf (Fingerabdruck).
     */
    @Scheduled(cron = "0 40 23 * * SUN", zone = "Europe/Berlin")
    public void runWeekly() {
        if (!props.isEnabled()) return;
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            log.warn("Tarif-Crawler übersprungen, Lock {} ist belegt; nächster Lauf in einer Woche", LOCK_NAME);
            return;
        }
        runReleasing(false);
    }

    /** Knopf im Admin-Tab: alle aktiven Quellen, im Hintergrund. false, wenn gerade ein Lauf aktiv ist. */
    public boolean tryStartManual() {
        if (!locks.tryAcquire(LOCK_NAME, LOCK_TTL)) return false;
        executor.execute(() -> runReleasing(false));
        return true;
    }

    private void runReleasing(boolean onlyDue) {
        try {
            TariffCrawlService.RunSummary summary = service.run(onlyDue, LocalDateTime.now());
            log.info("Tarif-Crawler: {} Quellen gelesen, {} Änderungen übernommen, {} zu entscheiden",
                    summary.statusBySource().size(), summary.applied(), summary.needAdmin().size());
            List<String> lines = lines(summary);
            if (!lines.isEmpty()) alerts.sendTariffCrawlAlert(lines);
        } catch (Exception e) {
            log.error("Tarif-Crawler abgebrochen", e);
        } finally {
            locks.release(LOCK_NAME);
        }
    }

    static List<String> lines(TariffCrawlService.RunSummary summary) {
        List<String> lines = new ArrayList<>();
        summary.needAdmin().forEach(p -> lines.add(describe(p)));
        if (!summary.extractionErrors().isEmpty()) {
            lines.add("Sprachmodell: " + String.join(", ", summary.extractionErrors())
                    + ". Die Seiten selbst waren lesbar, die Tarife bleiben unverändert.");
        }
        for (TariffSourceEntity s : summary.newlyFailing()) {
            lines.add(s.getEmpName() + ": Seite zweimal in Folge nicht lesbar ("
                    + Objects.toString(s.getLastError(), s.getLastStatus()) + "), " + s.getUrl());
        }
        return lines;
    }

    /** Ein Vorschlag als Satz für Mail und Admin-Tab. */
    public static String describe(TariffProposalEntity p) {
        Map<String, Object> v = p.getProposed();
        String tariff = (Objects.toString(v.get("variant"), "") + " " + Objects.toString(v.get("chargingType"), "")).trim();
        String text = switch (p.getKind()) {
            case CHANGE -> tariff + " neu " + euro(v.get("pricePerKwh")) + " €/kWh";
            case NEW -> "neuer Tarif " + tariff + " " + euro(v.get("pricePerKwh")) + " €/kWh";
            case MISSING -> tariff + " steht nicht mehr auf der Seite";
            case WATCH_CHANGED -> "Preisbilder oder Text der Seite geändert, bitte prüfen";
            case MAPPING -> v.get("operator") + " jetzt Preisstufe " + v.get("tier")
                    + (v.get("previousTier") == null ? "" : " (bisher " + v.get("previousTier") + ")");
        };
        boolean reasonAddsSomething = p.getHoldReason() != null && (p.getKind() == TariffProposalEntity.Kind.CHANGE);
        return p.getEmpName() + ": " + text + (reasonAddsSomething ? ". " + p.getHoldReason() : "");
    }

    private static String euro(Object value) {
        if (value == null) return "?";
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
