package com.evmonitor.application;

import com.evmonitor.domain.SubscriptionTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminAlertService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@ev-monitor.net}")
    private String fromAddress;

    @Value("${app.alert.email:}")
    private String alertEmail;

    /**
     * Comma-separated internal recipients for the purchase-celebration mail (the founders).
     * Empty = feature off. Kept out of the public repo via env-var (ALERT_PURCHASE_RECIPIENTS).
     */
    @Value("${app.alert.purchase-recipients:}")
    private String purchaseRecipients;

    /** Hand-written, rotated per send so the mail never gets stale. Pure flavor - the hard facts go below. */
    private static final String[] CELEBRATION_LINES = {
            "Kasse hat geklingelt! Jemand da draußen glaubt an uns - und zahlt sogar dafür.",
            "Yay, wieder ein Abo! Kurz durchatmen, kurz freuen, dann weiterbauen.",
            "Ka-ching. Ein Mensch mit exzellentem Geschmack hat gerade zugeschlagen. Whoop Whoop!",
            "Plot twist: es funktioniert wirklich. Frisches Abo eingetrudelt.",
            "Jemand hat 'ja' gesagt. MRR steigt, Laune steigt, Feierabendbier verdient.",
            "Neuer Abonnent an Bord. Die Vision lebt - und finanziert sich langsam selbst.",
            "Es ist passiert: echtes Vertrauen von einem echten Menschen. Genießt den Moment.",
            "Wieder einer, der uns sein Vertrauen (und ein paar Euro) schenkt. Whoop Whoop!"
    };

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

    /**
     * Internal, fire-and-forget "yay, a new subscription!" mail to the founders.
     * Triggered on a genuine new-customer purchase (inactive -> active). Never throws:
     * a mail failure must not break the Stripe webhook that triggered it.
     *
     * @param tier  the tier that was purchased
     * @param trial true if the subscription started as a trial (card given, not yet charged)
     */
    public void sendPurchaseCelebration(SubscriptionTier tier, boolean trial) {
        String[] recipients = parseRecipients(purchaseRecipients);
        if (recipients.length == 0) {
            log.debug("AdminAlert: keine Purchase-Empfaenger konfiguriert, ueberspringe Celebration");
            return;
        }
        try {
            String tierLabel = tierLabel(tier);
            String statusLabel = trial ? "Trial gestartet (Karte hinterlegt, noch nicht abgerechnet)" : "direkt bezahlt";
            String line = CELEBRATION_LINES[ThreadLocalRandom.current().nextInt(CELEBRATION_LINES.length)];

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(recipients);
            msg.setSubject((trial ? "Neuer Trial: " : "Neues Abo: ") + tierLabel + " - Whoop Whoop!");
            msg.setText(
                    line + "\n\n" +
                    "Tier:   " + tierLabel + "\n" +
                    "Status: " + statusLabel + "\n\n" +
                    "- eure ev-monitor Kasse"
            );
            mailSender.send(msg);
            log.info("AdminAlert: Purchase-Celebration gesendet (tier={}, trial={}, empfaenger={})",
                    tier, trial, recipients.length);
        } catch (Exception e) {
            log.error("AdminAlert: Purchase-Celebration konnte nicht gesendet werden", e);
        }
    }

    private static String[] parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private static String tierLabel(SubscriptionTier tier) {
        return switch (tier) {
            case AUTOSYNC -> "AutoSync";
            case AUTOSYNC_LIVE -> "AutoSync Live";
            case SUPPORTER -> "Supporter";
            case NONE -> "Kostenlos";
        };
    }
}
