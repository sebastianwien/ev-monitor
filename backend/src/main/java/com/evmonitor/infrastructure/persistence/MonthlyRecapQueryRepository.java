package com.evmonitor.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Empfänger und Versandmarker der Monatsrückblick-Mail.
 *
 * Gezählt werden dieselben Logs, die auch in die Statistik fließen (nicht gelöscht,
 * include_in_statistics), damit die Mail nie mit weniger als drei Ladungen im Text startet.
 */
@Repository
public class MonthlyRecapQueryRepository {

    public static final int MIN_CHARGES = 3;

    private final JdbcTemplate jdbc;

    public MonthlyRecapQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RecapCandidate(UUID userId, UUID carId) {}

    /**
     * Ein Auto pro Nutzer: das mit den meisten Ladungen im Monat (bei Gleichstand das ältere).
     * Nutzer, die für diesen Monat schon einen Marker haben, fallen heraus.
     */
    public List<RecapCandidate> findCandidates(LocalDate month) {
        LocalDate start = month.withDayOfMonth(1);
        return jdbc.query("""
                SELECT DISTINCT ON (u.id) u.id AS user_id, c.id AS car_id
                FROM app_user u
                JOIN car c ON c.user_id = u.id AND c.deleted_at IS NULL
                JOIN ev_log e ON e.car_id = c.id
                 AND e.deleted_at IS NULL
                 AND e.include_in_statistics = true
                 AND e.logged_at >= ? AND e.logged_at < ?
                WHERE u.email_verified = true
                  AND u.email_notifications_enabled = true
                  AND u.is_seed_data = false
                  AND NOT EXISTS (SELECT 1 FROM monthly_recap_sent s WHERE s.user_id = u.id AND s.month = ?)
                GROUP BY u.id, c.id, c.created_at
                HAVING count(*) >= ?
                ORDER BY u.id, count(*) DESC, c.created_at
                """,
                (rs, i) -> new RecapCandidate(rs.getObject("user_id", UUID.class), rs.getObject("car_id", UUID.class)),
                start.atStartOfDay(), start.plusMonths(1).atStartOfDay(), start, MIN_CHARGES);
    }

    /** Legt den Marker an. false, wenn ein anderer Lauf diesen Nutzer für den Monat schon hat. */
    public boolean claim(UUID userId, LocalDate month, UUID carId) {
        return jdbc.update("""
                INSERT INTO monthly_recap_sent (user_id, month, car_id) VALUES (?, ?, ?)
                ON CONFLICT (user_id, month) DO NOTHING
                """, userId, month.withDayOfMonth(1), carId) == 1;
    }

    /** Gibt den Marker frei, wenn der Versand gescheitert ist, damit der Nachhol-Lauf es erneut versucht. */
    public void release(UUID userId, LocalDate month) {
        jdbc.update("DELETE FROM monthly_recap_sent WHERE user_id = ? AND month = ?", userId, month.withDayOfMonth(1));
    }
}
