package com.evmonitor.infrastructure.email;

import com.evmonitor.infrastructure.security.JwtService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("EV Monitor – E-Mail bestätigen");
            helper.setText(buildHtml(verificationUrl), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send verification email to " + toEmail, e);
        }
    }

    private String buildHtml(String verificationUrl) {
        return """
                <!DOCTYPE html>
                <html lang="de">
                <body style="font-family: Arial, sans-serif; background-color: #f3f4f6; margin: 0; padding: 20px;">
                  <div style="max-width: 600px; margin: 40px auto; background: #ffffff; border-radius: 12px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.08);">
                    <h1 style="color: #4f46e5; font-size: 26px; margin-bottom: 4px;">⚡ EV Monitor</h1>
                    <h2 style="color: #1f2937; font-size: 20px; margin-top: 0; font-weight: 600;">Fast geschafft! Bestätige deine E-Mail.</h2>
                    <p style="color: #6b7280; line-height: 1.7; font-size: 15px;">
                      Klick auf den Button unten, um dein Konto zu aktivieren.<br>
                      Der Link ist <strong>24 Stunden</strong> gültig.
                    </p>
                    <div style="text-align: center; margin: 36px 0;">
                      <a href="%s"
                         style="display: inline-block; background-color: #4f46e5; color: #ffffff; padding: 14px 36px;
                                border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 16px;
                                letter-spacing: 0.3px;">
                        E-Mail bestätigen
                      </a>
                    </div>
                    <p style="color: #9ca3af; font-size: 13px; margin-top: 32px; border-top: 1px solid #e5e7eb; padding-top: 16px;">
                      Falls du dich nicht bei EV Monitor registriert hast, kannst du diese E-Mail einfach ignorieren.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(verificationUrl);
    }
}
