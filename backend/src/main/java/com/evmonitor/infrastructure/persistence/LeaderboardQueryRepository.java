package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.LeaderboardRankRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Native SQL queries for leaderboard aggregations.
 * Uses EntityManager directly because each category has a structurally different query.
 * All queries filter: include_in_statistics=true, is_seed_data=false, leaderboard_visible=true.
 */
@Repository
public class LeaderboardQueryRepository {

    @PersistenceContext
    private EntityManager em;

    // ---- MONTHLY_KWH ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getKwhRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(u.id AS TEXT), u.username, SUM(e.kwh_charged) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapRows(rows);
    }

    // ---- MONTHLY_CHARGES ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getChargesRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(u.id AS TEXT), u.username, CAST(COUNT(e.id) AS NUMERIC) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapRows(rows);
    }

    // ---- MONTHLY_DISTANCE (per-car max-min odometer, summed across cars per user) ----
    // Plausibility: per-car delta must be between 0 and 5000 km (prevents odometer resets
    // and wildly wrong manual entries from inflating the ranking).

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getDistanceRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(u.id AS TEXT), u.username,
                       CAST(SUM(car_dist.delta_km) AS NUMERIC) AS value
                FROM (
                    SELECT c.user_id,
                           MAX(e.odometer_km) - MIN(e.odometer_km) AS delta_km
                    FROM ev_log e
                    JOIN car c ON e.car_id = c.id
                    WHERE e.include_in_statistics = true
                      AND e.odometer_km IS NOT NULL
                      AND e.logged_at >= :start
                      AND e.logged_at < :end
                    GROUP BY c.id, c.user_id
                    HAVING COUNT(e.id) >= 2
                       AND MAX(e.odometer_km) - MIN(e.odometer_km) BETWEEN 1 AND 5000
                ) car_dist
                JOIN app_user u ON car_dist.user_id = u.id
                WHERE u.is_seed_data = false
                  AND u.leaderboard_visible = true
                GROUP BY u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapRows(rows);
    }

    // ---- MONTHLY_COINS ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getCoinsRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(u.id AS TEXT), u.username, CAST(COALESCE(SUM(cl.amount), 0) AS NUMERIC) AS value
                FROM coin_log cl
                JOIN app_user u ON cl.user_id = u.id
                WHERE u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND cl.created_at >= :start
                  AND cl.created_at < :end
                  AND cl.amount > 0
                GROUP BY u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapRows(rows);
    }

    // ---- MONTHLY_CHEAPEST (avg ct/kWh, lower is better, min 3 logs with cost data) ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getCheapestRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(u.id AS TEXT), u.username,
                       ROUND(AVG(e.cost_eur / NULLIF(e.kwh_charged, 0)) * 100, 2) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.cost_eur IS NOT NULL
                  AND e.kwh_charged > 0
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY u.id, u.username
                HAVING COUNT(e.id) >= 3
                ORDER BY value ASC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapRows(rows);
    }

    // ---- MONTHLY_NIGHT_OWL (charges between 22:00-06:00) ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getNightOwlRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(u.id AS TEXT), u.username, CAST(COUNT(e.id) AS NUMERIC) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                  AND (EXTRACT(HOUR FROM e.logged_at) >= 22 OR EXTRACT(HOUR FROM e.logged_at) < 6)
                GROUP BY u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapRows(rows);
    }

    // ---- MONTHLY_ICE_CHARGER (lowest temperature, lower = better) ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getIceChargerRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(u.id AS TEXT), u.username,
                       CAST(MIN(e.temperature_celsius) AS NUMERIC) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.temperature_celsius IS NOT NULL
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY u.id, u.username
                ORDER BY value ASC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapRows(rows);
    }

    // ---- MONTHLY_POWER_CHARGER (highest max_charging_power_kw) ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getPowerChargerRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(u.id AS TEXT), u.username,
                       MAX(e.max_charging_power_kw) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.max_charging_power_kw IS NOT NULL
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapRows(rows);
    }

    // ---- Community totals for ticker ----

    public BigDecimal getTotalKwhThisMonth(LocalDateTime start, LocalDateTime endExclusive) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(e.kwh_charged), 0)
                FROM ev_log e
                WHERE e.include_in_statistics = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getSingleResult();
        return result instanceof BigDecimal bd ? bd : new BigDecimal(result.toString());
    }

    public long getTotalChargesThisMonth(LocalDateTime start, LocalDateTime endExclusive) {
        Object result = em.createNativeQuery("""
                SELECT COUNT(e.id)
                FROM ev_log e
                WHERE e.include_in_statistics = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    public long getTotalChargeDurationMinutes(LocalDateTime start, LocalDateTime endExclusive) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(e.charge_duration_minutes), 0)
                FROM ev_log e
                WHERE e.include_in_statistics = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    public BigDecimal getTotalCostEur(LocalDateTime start, LocalDateTime endExclusive) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(e.cost_eur), 0)
                FROM ev_log e
                WHERE e.include_in_statistics = true
                  AND e.cost_eur IS NOT NULL
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getSingleResult();
        return result instanceof BigDecimal bd ? bd : new BigDecimal(result.toString());
    }

    // ---- Helpers ----

    private List<LeaderboardRankRow> mapRows(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new LeaderboardRankRow(
                        UUID.fromString((String) r[0]),
                        (String) r[1],
                        r[2] != null ? new BigDecimal(r[2].toString()) : BigDecimal.ZERO
                ))
                .toList();
    }
}
