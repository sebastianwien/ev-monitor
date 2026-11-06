package com.evmonitor.application.mail;

import java.util.Arrays;
import java.util.Optional;

/**
 * Katalog aller Mails an Nutzer: einzige Quelle für Zweck, Auslöser, Empfänger und Ziel-Goal.
 * {@code key} ist zugleich {@code utm_campaign} der Links (siehe EmailService#campaignUrl) und der Wert in
 * {@code mail_send_log.mail_type}: nie umbenennen, sonst reißen Verlauf und Plausible-Zuordnung ab.
 * {@code goal} ist ein Plausible-Goal aus analytics.ts, das in Plausible als Goal angelegt sein muss.
 * Doku: docs/features/mailings.md.
 */
public enum MailType {

    VERIFICATION("verification", "verification.html", Kind.PFLICHT, new Info("E-Mail bestätigen",
            "Adresse bestätigen, damit das Konto voll nutzbar ist.",
            "Registrierung mit Passwort", "Neue Nutzer", null)),
    LOGIN_CODE("login-code", "login-code.html", Kind.PFLICHT, new Info("Login-Code",
            "Sechsstelliger Code zum Anmelden ohne Passwort.",
            "Anmeldung per Code, z. B. am Gate der Modellseite", "Alle, die einen Code anfordern", null)),
    PASSWORD_RESET("password-reset", "password-reset.html", Kind.PFLICHT, new Info("Passwort zurücksetzen",
            "Link zum Setzen eines neuen Passworts.",
            "Passwort vergessen", "Nutzer, die es anfordern", null)),
    PERSONAL_RANGE_RESULT("personal-range-result", "personal-range-result.html", Kind.LEBENSZYKLUS, new Info("Ergebnis Fahrprofil-Rechner",
            "Das persönliche Ergebnis (Reichweite, Laden, Monatskosten) zum Nachlesen, mit Link zurück zur Modellseite.",
            "Direkt nach dem Gate auf der Modellseite", "Kaufinteressierte vom Fahrprofil-Rechner", null)),
    ONBOARDING_REMINDER("onboarding-reminder", "onboarding-reminder.html", Kind.LEBENSZYKLUS, new Info("Erinnerung erste Ladung",
            "Neue Nutzer zur ersten erfassten Ladung bringen.",
            "Täglicher Job: einige Tage nach der Registrierung", "Neue Nutzer mit bestätigter Adresse, nicht abgemeldet", "log_created")),
    RE_ENGAGEMENT("re-engagement", "re-engagement.html", Kind.LEBENSZYKLUS, new Info("Wiederaktivierung",
            "Inaktive Nutzer zurückholen und fragen, warum sie weg sind.",
            "Täglicher Job: länger keine Ladung erfasst", "Inaktive Nutzer, nicht abgemeldet", "log_created")),
    AUTOSYNC_SATISFACTION("autosync-satisfaction", "autosync-satisfaction.html", Kind.LEBENSZYKLUS, new Info("AutoSync-Zufriedenheit",
            "Kurze Umfrage, wie AutoSync läuft.",
            "Täglicher Job: einige Zeit nach Start von AutoSync", "AutoSync-Abonnenten", "survey_submitted")),
    AUTOSYNC_DORMANT("autosync-dormant", "autosync-dormant.html", Kind.LEBENSZYKLUS, new Info("AutoSync, aber nicht mehr reingeschaut",
            "AutoSync-Nutzer, die die App nicht mehr öffnen, an ihre Daten erinnern und nach dem Grund fragen.",
            "Täglicher Job: länger nicht eingeloggt trotz AutoSync", "AutoSync-Abonnenten", "survey_submitted")),
    TRIAL_ENDING("trial-ending", "trial-ending.html", Kind.PFLICHT, new Info("Testphase endet",
            "Hinweis 7 Tage vor Ende der Stripe-Testphase, wann sie endet und wo man kündigt.",
            "Täglicher Job: 7 Tage vor Testende", "Nutzer in langer Stripe-Testphase", null)),
    EUDA_HANDOVER("euda-handover", "euda-handover.html", Kind.LEBENSZYKLUS, new Info("VW Data Act übernimmt",
            "Erklären, dass das Auto jetzt über das VW-Portal meldet und Smartcar pausiert.",
            "Wechsel der Verbindung auf VW Data Act", "VW-Fahrer mit Data-Act-Verbindung", null)),
    EUDA_CONNECTION_LOST("euda-connection-lost", "euda-connection-lost.html", Kind.LEBENSZYKLUS, new Info("VW Data Act getrennt",
            "Zur erneuten Anmeldung beim VW-Portal bringen.",
            "Verbindung zum VW-Portal bricht ab", "VW-Fahrer mit Data-Act-Verbindung", "connector_activated")),
    EUDA_HISTORY_IMPORTED("euda-history-imported", "euda-history-imported.html", Kind.LEBENSZYKLUS, new Info("VW-Ladehistorie importiert",
            "Melden, dass die Ladehistorie da ist, und ins Logbuch führen.",
            "Import der Ladehistorie fertig", "VW-Fahrer mit Data-Act-Verbindung", null)),
    EUDA_TRIAL_ENDING("euda-trial-ending", "euda-trial-ending.html", Kind.LEBENSZYKLUS, new Info("VW-Data-Act-Test endet",
            "Vor Ende des kostenlosen Zeitraums zum Abo führen.",
            "Täglicher Job: kurz vor Testende", "VW-Fahrer im Testzeitraum", "checkout_completed")),
    EUDA_TRIAL_ENDED("euda-trial-ended", "euda-trial-ended.html", Kind.LEBENSZYKLUS, new Info("VW-Data-Act-Test beendet",
            "Nach Ende des Testzeitraums zum Abo führen.",
            "Täglicher Job: Testende erreicht", "VW-Fahrer nach dem Testzeitraum", "checkout_completed")),
    MONTHLY_RECAP("monthly-recap", "monthly-recap.html", Kind.LEBENSZYKLUS, new Info("Monatsrückblick",
            "Persönlicher Rückblick auf den Monat, mit Link zur Story und zum Nachtragen fehlender Preise.",
            "Monatlicher Job am Monatsanfang", "Nutzer mit Ladungen im Vormonat", "log_created")),
    RATING_REVIEW_REJECTED("rating-review-rejected", "rating-review-rejected.html", Kind.LEBENSZYKLUS, new Info("Erfahrungstext abgelehnt",
            "Grund nennen und zum Überarbeiten des Textes führen.",
            "Admin lehnt einen Erfahrungstext ab", "Autor des Textes", "rating_completed_deep")),
    MISSING_VEHICLE_DONE("missing-vehicle-done", "missing-vehicle-done.html", Kind.LEBENSZYKLUS, new Info("Fahrzeug ergänzt",
            "Melden, dass das gewünschte Fahrzeug jetzt da ist, und zum Anlegen des Autos führen.",
            "Admin erledigt eine Fahrzeug-Meldung", "Nutzer, die das Fahrzeug gemeldet haben", "car_added"));

    /** PFLICHT: geht an jeden, der sie auslöst, ohne Einwilligung und ohne Ziel-Goal. LEBENSZYKLUS: soll etwas bewirken. */
    public enum Kind { PFLICHT, LEBENSZYKLUS }

    /** Beschreibung für Doku und Admin-Tab; goal ist das Plausible-Goal, das die Mail bewirken soll, null = nur Info. */
    public record Info(String title, String purpose, String trigger, String audience, String goal) {}

    private final String key;
    private final String template;
    private final Kind kind;
    private final Info info;

    MailType(String key, String template, Kind kind, Info info) {
        this.key = key;
        this.template = template;
        this.kind = kind;
        this.info = info;
    }

    public String key() { return key; }
    public String template() { return template; }
    public Kind kind() { return kind; }
    public String title() { return info.title(); }
    public String purpose() { return info.purpose(); }
    public String trigger() { return info.trigger(); }
    public String audience() { return info.audience(); }
    /** Plausible-Goal, das die Mail bewirken soll; null, wenn sie nur informiert. */
    public String goal() { return info.goal(); }

    public static Optional<MailType> fromKey(String key) {
        return Arrays.stream(values()).filter(t -> t.key.equals(key)).findFirst();
    }
}
