package com.evmonitor.infrastructure.persistence.mail;

import com.evmonitor.application.mail.MailSendLog;
import com.evmonitor.application.mail.MailType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcMailSendLog implements MailSendLog {

    private final JdbcTemplate jdbc;

    public JdbcMailSendLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Eigene Transaktion: ein Rollback des Aufrufers (z. B. nach fehlgeschlagenem Versand) nimmt den Eintrag nicht mit. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(MailType type, String email, boolean sent) {
        jdbc.update("""
                INSERT INTO mail_send_log (mail_type, user_id, sent, sent_at)
                VALUES (?, (SELECT id FROM app_user WHERE lower(email) = lower(?) LIMIT 1), ?, ?)
                """, type.key(), email, sent, Timestamp.valueOf(LocalDateTime.now()));
    }

    @Override
    public List<SendStats> sendStats(LocalDateTime since) {
        return jdbc.query("""
                SELECT mail_type,
                       count(*) FILTER (WHERE sent) AS sent,
                       count(*) FILTER (WHERE NOT sent) AS failed,
                       max(sent_at) FILTER (WHERE sent) AS last_sent
                FROM mail_send_log WHERE sent_at >= ? GROUP BY mail_type
                """, (rs, i) -> {
            Timestamp last = rs.getTimestamp("last_sent");
            return new SendStats(rs.getString("mail_type"), rs.getLong("sent"), rs.getLong("failed"),
                    last == null ? null : last.toLocalDateTime());
        }, Timestamp.valueOf(since));
    }

    @Override
    public List<RecentSend> recent(MailType type, int limit) {
        return jdbc.query("""
                SELECT l.sent_at, l.sent, l.user_id, u.email
                FROM mail_send_log l LEFT JOIN app_user u ON u.id = l.user_id
                WHERE l.mail_type = ? ORDER BY l.sent_at DESC, l.id DESC LIMIT ?
                """, (rs, i) -> new RecentSend(rs.getTimestamp("sent_at").toLocalDateTime(), rs.getBoolean("sent"),
                rs.getObject("user_id", UUID.class), rs.getString("email")), type.key(), limit);
    }

    @Override
    public List<CampaignVisits> campaignVisits(LocalDate since) {
        return jdbc.query("""
                SELECT utm_campaign, sum(visits) AS visits FROM plausible_source_daily
                WHERE utm_medium = 'lifecycle' AND utm_campaign <> '' AND stat_date >= ? GROUP BY utm_campaign
                """, (rs, i) -> new CampaignVisits(rs.getString("utm_campaign"), rs.getLong("visits")), since);
    }

    @Override
    public List<CampaignGoal> campaignGoals(LocalDate since) {
        return jdbc.query("""
                SELECT utm_campaign, goal, sum(visitors) AS visitors FROM plausible_source_goal_daily
                WHERE utm_medium = 'lifecycle' AND utm_campaign <> '' AND stat_date >= ? GROUP BY utm_campaign, goal
                """, (rs, i) -> new CampaignGoal(rs.getString("utm_campaign"), rs.getString("goal"), rs.getLong("visitors")), since);
    }

    @Override
    public int deleteOlderThan(LocalDateTime cutoff) {
        return jdbc.update("DELETE FROM mail_send_log WHERE sent_at < ?", Timestamp.valueOf(cutoff));
    }
}
