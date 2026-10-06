package com.evmonitor.infrastructure.email;

import com.evmonitor.application.recap.MonthlyRecap;
import com.evmonitor.infrastructure.security.JwtService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

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
        String endsAtText = endsAt.format(DateTimeFormatter.ofPattern("en".equals(lang) ? "d MMMM yyyy" : "d. MMMM yyyy",
                "en".equals(lang) ? Locale.ENGLISH : Locale.GERMAN));
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
