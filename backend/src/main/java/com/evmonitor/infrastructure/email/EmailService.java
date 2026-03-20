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

    public void sendVerificationEmail(String toEmail, String token) {
        String verificationUrl = baseUrl + "/verify-email?token=" + token;
        String html = loadTemplate("verification.html", Map.of(
                "verificationUrl", verificationUrl
        ));
        sendHtmlEmail(toEmail, "EV Monitor – E-Mail bestätigen", html);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        String html = loadTemplate("password-reset.html", Map.of("resetUrl", resetUrl));
        sendHtmlEmail(toEmail, "EV Monitor – Passwort zurücksetzen", html);
    }

    public void sendReEngagementEmail(String toEmail, String username) {
        String html = loadTemplate("re-engagement.html", Map.of(
                "username", username,
                "dashboardUrl", baseUrl + "/dashboard",
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        sendHtmlEmail(toEmail, "Kurze Nachricht von mir", html);
    }

    public void sendOnboardingReminderEmail(String toEmail, String username) {
        String html = loadTemplate("onboarding-reminder.html", Map.of(
                "username", username,
                "dashboardUrl", baseUrl + "/dashboard",
                "unsubscribeUrl", buildUnsubscribeUrl(toEmail)
        ));
        sendHtmlEmail(toEmail, "EV Monitor – Alles bereit für dein erstes Ladetagebuch?", html);
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

    private String loadTemplate(String templateName, Map<String, String> variables) {
        try {
            ClassPathResource resource = new ClassPathResource("email-templates/" + templateName);
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
