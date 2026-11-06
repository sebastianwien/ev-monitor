package com.evmonitor.application.mail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Versandprotokoll: welche Mail wann an welchen Nutzer ging. Speichert keinen Inhalt und keine Adresse,
 * nur den Verweis auf den Nutzer (fällt mit dem Konto weg). Dazu die Plausible-Zahlen je utm_campaign.
 */
public interface MailSendLog {

    /** Nutzer wird über die Adresse gesucht; ohne Konto bleibt der Eintrag ohne Nutzer. */
    void record(MailType type, String email, boolean sent);

    List<SendStats> sendStats(LocalDateTime since);

    /** Neueste zuerst; email ist die aktuelle Adresse des Kontos, null ohne Konto. */
    List<RecentSend> recent(MailType type, int limit);

    /** Besuche aus Mail-Links (utm_medium=lifecycle) je Kampagne ab since. */
    List<CampaignVisits> campaignVisits(LocalDate since);

    /** Besucher je Kampagne und Goal ab since. */
    List<CampaignGoal> campaignGoals(LocalDate since);

    int deleteOlderThan(LocalDateTime cutoff);

    record SendStats(String mailType, long sent, long failed, LocalDateTime lastSentAt) {}

    record RecentSend(LocalDateTime sentAt, boolean sent, UUID userId, String email) {}

    record CampaignVisits(String campaign, long visits) {}

    record CampaignGoal(String campaign, String goal, long visitors) {}
}
