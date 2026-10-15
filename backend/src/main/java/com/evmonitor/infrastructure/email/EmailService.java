package com.evmonitor.infrastructure.email;

import com.evmonitor.application.charging.LongTripPlan;
import com.evmonitor.application.personalrange.ChargeRhythm;
import com.evmonitor.application.personalrange.PersonalRangeRequest;
import com.evmonitor.application.personalrange.PersonalRangeResponse;
import com.evmonitor.application.recap.MonthlyRecap;
import com.evmonitor.infrastructure.security.JwtService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final JwtService jwtService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@ev-monitor.net}")
    private String fromAddress;

    /** Antwortadresse für persönliche Mails (Monatsrückblick). Leer = Antworten gehen an den Absender. */
    @Value("${app.mail.personal-reply-to:}")
    private String personalReplyTo;

    public EmailService(JavaMailSender mailSender, JwtService jwtService) {
        this.mailSender = mailSender;
        this.jwtService = jwtService;
    }

    public String buildUnsubscribeUrl(String email) {
        String token = jwtService.generateUnsubscribeToken(email);
        return baseUrl + "/api/unsubscribe?token=" + token;
    }

    /**
     * Link for a lifecycle mail, tagged with UTM parameters so Plausible attributes the visit
     * to the mail instead of "Direct / None" (mail clients usually send no referrer).
     * Transactional links (verification, password reset, unsubscribe) stay untagged.
     */
    private String campaignUrl(String path, String campaign) {
        return baseUrl + path + "?utm_source=email&utm_medium=lifecycle&utm_campaign=" + campaign;
    }

    /**
     * Normalizes a raw locale string (e.g. "en-US", "en", "de-DE", null) to "en" or "de".
     * Defaults to "de" for any unrecognized or null locale.
     */
    /** "8. November 2026" or "8 November 2026". */
    private static String longDate(LocalDate date, String lang) {
        return "en".equals(lang)
                ? date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
                : date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN));
    }

    private String resolveLocale(String rawLocale) {
        if (rawLocale != null && rawLocale.toLowerCase().startsWith("en")) {
            return "en";
        }
        return "de";
    }

    public void sendVerificationEmail(String toEmail, String token, String locale) {
        String lang = resolveLocale(locale);
        String verificationUrl = baseUrl + "/verify-email?token=" + token;
        String html = loadTemplate("verification.html", lang, Map.of(
                "verificationUrl", verificationUrl
        ));
        String subject = "en".equals(lang)
                ? "EV Monitor - Confirm your email"
                : "EV Monitor - E-Mail bestätigen";
        sendHtmlEmail(toEmail, subject, html);
    }

    /** Six-digit login code for the gate on the model page. The code goes into subject and body. */
    public void sendLoginCodeEmail(String toEmail, String code, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("login-code.html", lang, Map.of("code", code));
        String subject = "en".equals(lang) ? "Your code: " + code : "Dein Code: " + code;
        try {
            sendHtmlEmail(toEmail, subject, html);
        } catch (RuntimeException e) {
            // Default message carries the subject, and the subject carries the code: never log it
            throw new IllegalStateException("Failed to send login code to " + toEmail, e.getCause());
        }
    }

    /**
     * The personal range result, sent once right after the gate on the model page. Numbers only,
     * the measured-or-estimated list stays on the page.
     * @param modelPath path of the model page, e.g. {@code /modelle/Volkswagen/ID.3}
     */
    public void sendPersonalRangeResultEmail(String toEmail, String locale, String modelName, String modelPath,
                                             PersonalRangeMailContent content) {
        String lang = resolveLocale(locale);
        PersonalRangeRequest in = content.inputs();
        PersonalRangeResponse r = content.result();
        Map<String, String> vars = new HashMap<>();
        vars.put("model", modelName);
        vars.put("modelUrl", baseUrl + modelPath);
        // The yearly mileage drives the cost, the commute the weekly charging: the profile names both
        String commute = "en".equals(lang)
                ? "commuting " + in.commuteKmPerDay() + " km on " + in.commuteDays() + " days"
                : "Pendeln " + in.commuteKmPerDay() + " km an " + in.commuteDays() + " Tagen";
        vars.put("mileage", in.annualKm() != null ? annualKm(in.annualKm(), lang) + ", " + commute : commute);
        vars.put("highwayPercent", String.valueOf(in.highwaySharePercent()));
        vars.put("longTripKm", String.valueOf(in.longTripKm()));
        vars.put("winterRangeKm", String.valueOf(r.winterRangeKm()));
        vars.put("summerRangeKm", String.valueOf(r.summerRangeKm()));
        vars.put("travelTime", duration(r.longTripTotalMinutes()));
        vars.put("tripDetail", tripDetail(r, lang));
        vars.put("chargesSummer", ChargeRhythm.text(r.chargesPerWeekSummer(), in.homeCharging(), lang));
        vars.put("chargesWinter", ChargeRhythm.text(r.chargesPerWeekWinter(), in.homeCharging(), lang));
        vars.put("monthlyCostEur", String.valueOf(r.monthlyCostEur()));
        vars.put("pricePerKwh", pricePerKwh(r, lang));
        vars.put("costBasis", in.annualKm() != null
                ? annualKm(in.annualKm(), lang)
                : ("en".equals(lang) ? "commuting on 22 days" : "Pendeln an 22 Tagen"));
        String html = loadTemplate("personal-range-result.html", lang, vars);
        String subject = "en".equals(lang) ? "Your result for the " + modelName : "Dein Ergebnis für den " + modelName;
        sendHtmlEmail(toEmail, subject, html);
    }

    /** "8 h 5 min", "45 min", "9 h": units read the same in German and English */
    private static String duration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    /** "inkl. 2 Ladestopps, zusammen grob 50 min · Ø 110 km/h" under the travel time */
    private static String tripDetail(PersonalRangeResponse r, String lang) {
        int stops = r.longTripStops(), minutes = r.longTripChargingMinutes(), speed = LongTripPlan.TRAVEL_SPEED_KMH;
        // without the model's own fast-charge measurement the stop time rests on the fleet median: say so here
        boolean estimated = stops > 0 && r.assumptions().stream()
                .anyMatch(a -> a.key().equals("CHARGING_POWER") && !a.measured());
        if ("en".equals(lang)) {
            String charging = stops == 0 ? "no charging stop"
                    : stops == 1 ? "incl. 1 charging stop, roughly " + minutes + " min"
                    : "incl. " + stops + " charging stops, roughly " + minutes + " min in total";
            return charging + (estimated ? ", charging power estimated" : "") + " · avg " + speed + " km/h";
        }
        String charging = stops == 0 ? "ohne Ladestopp"
                : stops == 1 ? "inkl. 1 Ladestopp, grob " + minutes + " min"
                : "inkl. " + stops + " Ladestopps, zusammen grob " + minutes + " min";
        return charging + (estimated ? ", Ladeleistung geschätzt" : "") + " · Ø " + speed + " km/h";
    }

    /** "30.000 km im Jahr" / "30,000 km a year" */
    private static String annualKm(int km, String lang) {
        return "en".equals(lang) ? String.format(Locale.US, "%,d km a year", km) : String.format(Locale.GERMANY, "%,d km im Jahr", km);
    }

    /** The price the cost line rests on (own or community), as "0,30" / "0.30"; empty when not listed. */
    private static String pricePerKwh(PersonalRangeResponse r, String lang) {
        return r.assumptions().stream()
                .filter(a -> a.key().equals("OWN_PRICE") || a.key().equals("PRICE"))
                .map(a -> a.value().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .map(v -> "en".equals(lang) ? v : v.replace('.', ','))
                .findFirst().orElse("");
    }

    /** Inputs and result of one calculation, for the result mail. */
    public record PersonalRangeMailContent(PersonalRangeRequest inputs, PersonalRangeResponse result) {}

    /** Reminder 7 days before a long Stripe trial ends: when it ends and where to cancel. */
    public void sendTrialEndingEmail(String toEmail, String username, String locale, LocalDate endsAt) {
        String lang = resolveLocale(locale);
        String endsAtText = longDate(endsAt, lang);
        String html = loadTemplate("trial-ending.html", lang, Map.of(
                "username", username,
                "endsAt", endsAtText,
                "settingsUrl", baseUrl + "/settings"
        ));
        String subject = "en".equals(lang)
                ? "Your AutoSync trial ends on " + endsAtText
                : "Dein AutoSync-Test endet am " + endsAtText;
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendPasswordResetEmail(String toEmail, String token, String locale) {
        String lang = resolveLocale(locale);
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        String html = loadTemplate("password-reset.html", lang, Map.of("resetUrl", resetUrl));
        String subject = "en".equals(lang)
                ? "EV Monitor - Reset your password"
                : "EV Monitor - Passwort zurücksetzen";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendReEngagementEmail(String toEmail, String username, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("re-engagement.html", lang, Map.of(
                "username", username,
                "dashboardUrl", campaignUrl("/dashboard", "re-engagement"),
                "surveyUrl", campaignUrl("/umfrage/why-away", "re-engagement"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "A quick note from me"
                : "Kurze Nachricht von mir";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendAutoSyncAnnouncementEmail(String toEmail, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("autosync-announcement.html", lang, Map.of(
                "upgradeUrl", campaignUrl("/upgrade", "autosync-announcement"),
                "consumptionMethodologyUrl", campaignUrl("/consumption-methodology", "autosync-announcement"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "Hey, there's something cool and new!"
                : "Hey, es gibt was cooles Neues!";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendAutoSyncSatisfactionEmail(String toEmail, String username, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("autosync-satisfaction.html", lang, Map.of(
                "username", username,
                "surveyUrl", campaignUrl("/umfrage/autosync-satisfaction", "autosync-satisfaction"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "How is AutoSync working for you?"
                : "Wie läuft AutoSync für dich?";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendAutoSyncDormantEmail(String toEmail, String username, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("autosync-dormant.html", lang, Map.of(
                "username", username,
                "dashboardUrl", campaignUrl("/dashboard", "autosync-dormant"),
                "surveyUrl", campaignUrl("/umfrage/why-away", "autosync-dormant"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "Your car's been busy while you were away"
                : "Dein Auto war fleißig, während du weg warst";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendOnboardingReminderEmail(String toEmail, String username, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("onboarding-reminder.html", lang, Map.of(
                "username", username,
                "dashboardUrl", campaignUrl("/dashboard", "onboarding-reminder"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "EV Monitor - Ready for your first charging log?"
                : "EV Monitor - Alles bereit für dein erstes Ladetagebuch?";
        sendHtmlEmail(toEmail, subject, html);
    }

    // ── EU Data Act AutoSync (VW Group) ──────────────────────────────────────────

    public void sendVwEudaHandoverEmail(String toEmail, String username, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("euda-handover.html", lang, Map.of(
                "username", username,
                "logbookUrl", campaignUrl("/dashboard", "euda-handover"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "Your car now reports via the VW Data Act portal - Smartcar paused"
                : "Dein Auto meldet jetzt über das VW-Data-Act-Portal - Smartcar pausiert";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendVwEudaConnectionLostEmail(String toEmail, String username, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("euda-connection-lost.html", lang, Map.of(
                "username", username,
                "reconnectUrl", campaignUrl("/dashboard", "euda-connection-lost"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "VW Data Act connection interrupted - please sign in again"
                : "VW-Data-Act-Verbindung unterbrochen - bitte einmal neu anmelden";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendVwEudaHistoryImportedEmail(String toEmail, String username, String locale,
                                                  int imported, int skipped) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("euda-history-imported.html", lang, Map.of(
                "username", username,
                "imported", String.valueOf(imported),
                "skipped", String.valueOf(skipped),
                "logbookUrl", campaignUrl("/dashboard", "euda-history-imported"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "Your charging history is here: " + imported + " sessions imported"
                : "Deine Ladehistorie ist da: " + imported + " Ladevorgänge importiert";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendVwEudaTrialEndingEmail(String toEmail, String username, String locale, LocalDate endsAt) {
        String lang = resolveLocale(locale);
        String endsAtText = longDate(endsAt, lang);
        String html = loadTemplate("euda-trial-ending.html", lang, Map.of(
                "username", username,
                "endsAt", endsAtText,
                "upgradeUrl", campaignUrl("/upgrade", "euda-trial-ending"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "Your VW Data Act AutoSync trial ends on " + endsAtText
                : "Dein VW-Data-Act-AutoSync-Test endet am " + endsAtText;
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendVwEudaTrialEndedEmail(String toEmail, String username, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("euda-trial-ended.html", lang, Map.of(
                "username", username,
                "upgradeUrl", campaignUrl("/upgrade", "euda-trial-ended"),
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "VW Data Act AutoSync paused - your trial has ended"
                : "VW-Data-Act-AutoSync pausiert - dein Test ist zu Ende";
        sendHtmlEmail(toEmail, subject, html);
    }

    // ── Monatsrückblick ─────────────────────────────────────────────────────────────

    /**
     * Persönliche Monatsmail. Trägt List-Unsubscribe plus List-Unsubscribe-Post (RFC 8058), damit
     * Gmail und Apple Mail einen Abmelden-Knopf zeigen, der ohne Login per POST abmeldet.
     */
    public void sendMonthlyRecapEmail(MonthlyRecap recap) {
        String unsubscribeUrl = buildUnsubscribeUrl(recap.email());
        // Öffnet auf /logs das Modal "Preise nachtragen" für genau dieses Auto (LogsView, pricelessDeepLink).
        String priceUrl = campaignUrl("/logs", "monthly-recap") + "&car=" + recap.carId() + "&nachtragen=preis";
        MonthlyRecapMail.Rendered mail = MonthlyRecapMail.render(recap, unsubscribeUrl, priceUrl);
        String senderName = MonthlyRecapMail.isEnglish(recap.locale())
                ? "Sebastian from ev-monitor"
                : "Sebastian von ev-monitor";
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, senderName);
            if (personalReplyTo != null && !personalReplyTo.isBlank()) {
                helper.setReplyTo(personalReplyTo);
            }
            helper.setTo(recap.email());
            helper.setSubject(mail.subject());
            helper.setText(mail.html(), true);
            message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email '" + mail.subject() + "' to " + recap.email(), e);
        }
    }

    private static final Map<String, String> REJECTION_REASONS_DE = Map.of(
            "PERSONAL_DATA", "Der Text enthält persönliche Daten (zum Beispiel Namen oder Kennzeichen).",
            "INSULT", "Der Text greift Personen an oder ist beleidigend.",
            "ADVERTISING", "Der Text wirbt für ein Unternehmen oder enthält Empfehlungscodes.",
            "WRONG_MODEL", "Der Text beschreibt ein anderes Modell.",
            "NO_EXPERIENCE", "Im Text ist keine eigene Erfahrung mit dem Auto erkennbar.",
            "OTHER", "Der Text passt nicht zu unseren Regeln.");
    private static final Map<String, String> REJECTION_REASONS_EN = Map.of(
            "PERSONAL_DATA", "The text contains personal data (for example names or licence plates).",
            "INSULT", "The text attacks people or is offensive.",
            "ADVERTISING", "The text advertises a company or contains referral codes.",
            "WRONG_MODEL", "The text describes a different model.",
            "NO_EXPERIENCE", "The text shows no personal experience with the car.",
            "OTHER", "The text does not fit our rules.");

    /** Fahrzeugbewertung: Erfahrungstext abgelehnt, mit Grund und Link zum Bearbeiten. Ohne Abmeldelink (Antwort auf eigenen Inhalt). */
    public void sendReviewRejectedEmail(ReviewRejectedMail mail) {
        String lang = resolveLocale(mail.locale());
        Map<String, String> reasons = "en".equals(lang) ? REJECTION_REASONS_EN : REJECTION_REASONS_DE;
        String editPath = mail.carId() == null ? "/cars" : "/cars/" + mail.carId() + "/bewerten";
        boolean hasNote = mail.note() != null && !mail.note().isBlank();
        String html = EmailTemplateRenderer.render("rating-review-rejected.html", lang, Map.of(
                "username", mail.username(),
                "model", mail.modelName(),
                "reason", reasons.getOrDefault(mail.reason(), reasons.get("OTHER")),
                "note", hasNote ? mail.note() : "",
                "editUrl", campaignUrl(editPath, "rating-review-rejected")
        ), hasNote ? Set.of("note") : Set.of());
        String subject = "en".equals(lang)
                ? "Your text about the " + mail.modelName() + " was not published"
                : "Dein Text zum " + mail.modelName() + " wurde nicht veröffentlicht";
        sendHtmlEmail(mail.toEmail(), subject, html);
    }

    /**
     * Rückmeldung zur Fahrzeug-Meldung "Fehlt etwas?": die Zeile ist angelegt, der Nutzer kann sein Auto anlegen.
     * Löst nur ein Admin aus. vehicleLabel ist der Nutzertext der Meldung, das Template escaped ihn.
     */
    public void sendMissingVehicleDoneEmail(String toEmail, String locale, String vehicleLabel) {
        String lang = resolveLocale(locale);
        String html = EmailTemplateRenderer.render("missing-vehicle-done.html", lang, Map.of(
                "vehicle", vehicleLabel,
                "carsUrl", campaignUrl("/cars", "missing-vehicle-done")
        ));
        String subject = "en".equals(lang)
                ? vehicleLabel + " is now available - add your car"
                : vehicleLabel + " ist jetzt da - leg dein Auto an";
        sendHtmlEmail(toEmail, subject, html);
    }

    private void sendHtmlEmail(String toEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email '" + subject + "' to " + toEmail, e);
        }
    }

    private String loadTemplate(String templateName, String lang, Map<String, String> variables) {
        return EmailTemplateRenderer.render(templateName, lang, variables);
    }
}
