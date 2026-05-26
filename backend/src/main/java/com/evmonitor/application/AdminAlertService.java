package com.evmonitor.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminAlertService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@ev-monitor.net}")
    private String fromAddress;

    @Value("${app.alert.email:}")
    private String alertEmail;

    public void sendXpengEncryptionAlert(UUID connectionId, String maskedVin, String errorMessage) {
        if (alertEmail == null || alertEmail.isBlank()) {
            log.debug("AdminAlert: kein Alert-Email konfiguriert, ueberspringe");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(alertEmail);
            msg.setSubject("EV Monitor - XPeng XLSX Passwort-Fehler: " + maskedVin);
            msg.setText(
                    "Ein XPeng-XLSX-Import ist an einem Passwort-Problem gescheitert.\n\n" +
                    "Connection: " + connectionId + "\n" +
                    "VIN: " + maskedVin + "\n\n" +
                    "Fehler:\n" + errorMessage + "\n\n" +
                    "Bitte das Passwort in der Mailbox prüfen und ggf. manuell eintragen."
            );
            mailSender.send(msg);
            log.info("AdminAlert: Passwort-Fehler-Alert gesendet fuer connection={}", connectionId);
        } catch (Exception e) {
            log.error("AdminAlert: Alert-Mail konnte nicht gesendet werden", e);
        }
    }
}
