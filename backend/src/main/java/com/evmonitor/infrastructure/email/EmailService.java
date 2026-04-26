package com.evmonitor.infrastructure.email;

import com.evmonitor.infrastructure.security.JwtService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final JwtService jwtService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@ev-monitor.net}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender, JwtService jwtService) {
        this.mailSender = mailSender;
        this.jwtService = jwtService;
    }

    public String buildUnsubscribeUrl(String email) {
        String token = jwtService.generateUnsubscribeToken(email);
        return baseUrl + "/api/unsubscribe?token=" + token;
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
                "dashboardUrl", baseUrl + "/dashboard",
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
                "upgradeUrl", baseUrl + "/upgrade",
                "consumptionMethodologyUrl", baseUrl + "/consumption-methodology",
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "Hey, there's something cool and new!"
                : "Hey, es gibt was cooles Neues!";
        sendHtmlEmail(toEmail, subject, html);
    }

    public void sendOnboardingReminderEmail(String toEmail, String username, String locale) {
        String lang = resolveLocale(locale);
        String html = loadTemplate("onboarding-reminder.html", lang, Map.of(
                "username", username,
                "dashboardUrl", baseUrl + "/dashboard",
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        String subject = "en".equals(lang)
                ? "EV Monitor - Ready for your first charging log?"
                : "EV Monitor - Alles bereit für dein erstes Ladetagebuch?";
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

    /**
     * Loads a template from email-templates/{locale}/{templateName}.
     * Falls back to email-templates/{templateName} if the locale-specific file doesn't exist.
     */
    private String loadTemplate(String templateName, String lang, Map<String, String> variables) {
        try {
            ClassPathResource localeResource = new ClassPathResource("email-templates/" + lang + "/" + templateName);
            ClassPathResource fallbackResource = new ClassPathResource("email-templates/" + templateName);

            ClassPathResource resource = localeResource.exists() ? localeResource : fallbackResource;
            String template = resource.getContentAsString(StandardCharsets.UTF_8);

            for (Map.Entry<String, String> entry : variables.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", escapeHtml(entry.getValue()));
            }
            return template;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load email template: " + templateName, e);
        }
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
