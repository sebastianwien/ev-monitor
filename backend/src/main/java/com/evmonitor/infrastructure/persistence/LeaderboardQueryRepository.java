package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.ChargeCountStats;
import com.evmonitor.application.LeaderboardRankRow;
import com.evmonitor.application.TopCpoResult;
import com.evmonitor.domain.CarBrand;
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
 * All car-based queries group by car (not user) so each entry represents one vehicle.
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
                SELECT CAST(c.id AS TEXT), CAST(u.id AS TEXT), u.username, c.model,
                       SUM(COALESCE(e.kwh_at_vehicle, e.kwh_charged)) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY c.id, c.model, u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapCarRows(rows);
    }

    // ---- MONTHLY_CHARGES ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getChargesRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(c.id AS TEXT), CAST(u.id AS TEXT), u.username, c.model,
                       CAST(COUNT(e.id) AS NUMERIC) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY c.id, c.model, u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapCarRows(rows);
    }

    // ---- MONTHLY_DISTANCE (per-car max-min odometer) ----
    // Plausibility: delta must be between 0 and 5000 km per car per month.

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getDistanceRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(car_dist.car_id AS TEXT), CAST(u.id AS TEXT), u.username, car_dist.car_model,
                       CAST(car_dist.delta_km AS NUMERIC) AS value
                FROM (
                    SELECT c.id AS car_id, c.user_id, c.model AS car_model,
                           MAX(e.odometer_km) - MIN(e.odometer_km) AS delta_km
                    FROM ev_log e
                    JOIN car c ON e.car_id = c.id
                    WHERE e.include_in_statistics = true
                      AND e.odometer_km IS NOT NULL
                      AND e.logged_at >= :start
                      AND e.logged_at < :end
                    GROUP BY c.id, c.user_id, c.model
                    HAVING COUNT(e.id) >= 2
                       AND MAX(e.odometer_km) - MIN(e.odometer_km) BETWEEN 1 AND 5000
                ) car_dist
                JOIN app_user u ON car_dist.user_id = u.id
                WHERE u.is_seed_data = false
                  AND u.leaderboard_visible = true
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapCarRows(rows);
    }

    // ---- MONTHLY_COINS (user-based, not per-car) ----

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
        return mapUserRows(rows);
    }

    // ---- MONTHLY_CHEAPEST (avg ct/kWh, lower is better, min 3 logs with cost data) ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getCheapestRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(c.id AS TEXT), CAST(u.id AS TEXT), u.username, c.model,
                       ROUND(SUM(e.cost_eur) / NULLIF(SUM(COALESCE(e.kwh_charged,
                           e.kwh_at_vehicle / CASE
                               WHEN e.charging_type = 'DC'  THEN 0.95
                               WHEN e.charging_type = 'AC'  THEN 0.90
                               WHEN e.max_charging_power_kw > 22 THEN 0.95
                               WHEN e.max_charging_power_kw IS NOT NULL THEN 0.90
                               WHEN e.charge_duration_minutes > 0
                                    AND e.kwh_at_vehicle / (e.charge_duration_minutes / 60.0) > 22 THEN 0.95
                               WHEN e.charge_duration_minutes > 0 THEN 0.90
                               WHEN e.is_public_charging = true THEN 0.95
                               ELSE 0.90
                           END
                       )), 0) * 100, 2) AS value,
                       SUM(COALESCE(e.kwh_at_vehicle, e.kwh_charged)) AS kwh_total,
                       COUNT(e.id) AS session_count
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.cost_eur IS NOT NULL
                  AND COALESCE(e.kwh_charged, e.kwh_at_vehicle) > 0
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY c.id, c.model, u.id, u.username
                HAVING COUNT(e.id) >= 3
                ORDER BY value ASC, SUM(COALESCE(e.kwh_at_vehicle, e.kwh_charged)) DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapCheapestRows(rows);
    }

    // ---- MONTHLY_NIGHT_OWL (charges between 22:00-06:00) ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getNightOwlRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(c.id AS TEXT), CAST(u.id AS TEXT), u.username, c.model,
                       CAST(COUNT(e.id) AS NUMERIC) AS value
                FROM ev_log e
                JOIN car c ON e.car_id = c.id
                JOIN app_user u ON c.user_id = u.id
                WHERE e.include_in_statistics = true
                  AND u.is_seed_data = false
                  AND u.leaderboard_visible = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                  AND (EXTRACT(HOUR FROM e.logged_at) >= 22 OR EXTRACT(HOUR FROM e.logged_at) < 6)
                GROUP BY c.id, c.model, u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapCarRows(rows);
    }

    // ---- MONTHLY_ICE_CHARGER (lowest temperature, lower = better) ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getIceChargerRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(c.id AS TEXT), CAST(u.id AS TEXT), u.username, c.model,
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
                GROUP BY c.id, c.model, u.id, u.username
                ORDER BY value ASC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapCarRows(rows);
    }

    // ---- MONTHLY_POWER_CHARGER (highest max_charging_power_kw) ----

    @SuppressWarnings("unchecked")
    public List<LeaderboardRankRow> getPowerChargerRanking(LocalDateTime start, LocalDateTime endExclusive) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT CAST(c.id AS TEXT), CAST(u.id AS TEXT), u.username, c.model,
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
                GROUP BY c.id, c.model, u.id, u.username
                ORDER BY value DESC
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        return mapCarRows(rows);
    }

    // ---- Community totals for ticker ----

    public BigDecimal getTotalKwhThisMonth(LocalDateTime start, LocalDateTime endExclusive) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(COALESCE(e.kwh_at_vehicle, e.kwh_charged)), 0)
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

    public ChargeCountStats getChargeCountStats(LocalDateTime start, LocalDateTime endExclusive) {
        Object[] row = (Object[]) em.createNativeQuery("""
                SELECT COUNT(e.id),
                       COUNT(e.id) FILTER (WHERE e.is_public_charging = false)
                FROM ev_log e
                WHERE e.include_in_statistics = true
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getSingleResult();
        return new ChargeCountStats(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
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

    public TopCpoResult getTopPublicCpo(LocalDateTime start, LocalDateTime endExclusive) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT e.cpo_name, COUNT(e.id) AS cnt
                FROM ev_log e
                WHERE e.include_in_statistics = true
                  AND e.is_public_charging = true
                  AND e.cpo_name IS NOT NULL
                  AND e.logged_at >= :start
                  AND e.logged_at < :end
                GROUP BY e.cpo_name
                HAVING COUNT(e.id) >= 3
                ORDER BY cnt DESC
                LIMIT 1
                """)
                .setParameter("start", start)
                .setParameter("end", endExclusive)
                .getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = rows.get(0);
        return new TopCpoResult((String) row[0], ((Number) row[1]).longValue());
    }

    // ---- Helpers ----

    /** Maps car-based query rows: [carId, userId, username, carModel, value] */
    private List<LeaderboardRankRow> mapCarRows(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new LeaderboardRankRow(
                        UUID.fromString((String) r[0]),
                        UUID.fromString((String) r[1]),
                        (String) r[2],
                        carLabelFromModel((String) r[3]),
                        r[4] != null ? new BigDecimal(r[4].toString()) : BigDecimal.ZERO,
                        null,
                        null
                ))
                .toList();
    }

    /** Maps cheapest-ranking rows: [carId, userId, username, carModel, value, kwhTotal, sessionCount] */
    private List<LeaderboardRankRow> mapCheapestRows(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new LeaderboardRankRow(
                        UUID.fromString((String) r[0]),
                        UUID.fromString((String) r[1]),
                        (String) r[2],
                        carLabelFromModel((String) r[3]),
                        r[4] != null ? new BigDecimal(r[4].toString()) : BigDecimal.ZERO,
                        r[5] != null ? new BigDecimal(r[5].toString()) : null,
                        r[6] != null ? ((Number) r[6]).longValue() : null
                ))
                .toList();
    }

    /** Maps user-based query rows (coins): [userId, username, value] */
    private List<LeaderboardRankRow> mapUserRows(List<Object[]> rows) {
        return rows.stream()
                .map(r -> {
                    UUID userId = UUID.fromString((String) r[0]);
                    return new LeaderboardRankRow(
                            userId,
                            userId,
                            (String) r[1],
                            null,
                            r[2] != null ? new BigDecimal(r[2].toString()) : BigDecimal.ZERO,
                            null,
                            null
                    );
                })
                .toList();
    }

    private String carLabelFromModel(String modelName) {
        try {
            CarBrand.CarModel m = CarBrand.CarModel.valueOf(modelName);
            return m.getBrand().getDisplayString() + " " + m.getDisplayName();
        } catch (IllegalArgumentException e) {
            return modelName;
        }
    }
}
