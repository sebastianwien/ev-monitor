package com.evmonitor.infrastructure.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends alert emails for unexpected errors.
 * Rate-limited to 1 email per unique error key per hour (prevents flood on cascading failures).
 */
@Service
public class AlertEmailService {

    private static final Logger log = LoggerFactory.getLogger(AlertEmailService.class);
    private static final Duration COOLDOWN = Duration.ofHours(1);

    private final JavaMailSender mailSender;
    private final Map<String, Instant> lastAlertTime = new ConcurrentHashMap<>();

    @Value("${app.alert.email:}")
    private String alertEmail;

    @Value("${app.mail.from:noreply@ev-monitor.net}")
    private String fromAddress;

    public AlertEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * @param errorKey  Deduplication key (e.g. exception class name or error message prefix)
     * @param subject   Email subject
     * @param body      Plain-text email body
     */
    public void sendAlert(String errorKey, String subject, String body) {
        if (alertEmail == null || alertEmail.isBlank()) {
            log.warn("ALERT (no email configured): {}", subject);
            return;
        }

        Instant now = Instant.now();
        Instant last = lastAlertTime.get(errorKey);
        if (last != null && Duration.between(last, now).compareTo(COOLDOWN) < 0) {
            log.debug("Alert rate-limited for key '{}' (last sent: {})", errorKey, last);
            return;
        }
        lastAlertTime.put(errorKey, now);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(alertEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Alert sent to {}: {}", alertEmail, subject);
        } catch (Exception e) {
            log.error("Failed to send alert email: {}", e.getMessage());
        }
    }
}
