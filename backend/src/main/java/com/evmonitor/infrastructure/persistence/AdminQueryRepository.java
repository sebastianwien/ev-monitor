package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.AdminChargingActivityRow;
import com.evmonitor.application.AdminUserGrowthRow;
import com.evmonitor.application.AdminUserRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class AdminQueryRepository {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public List<AdminUserRow> getUserTable() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT au.email,
                       au.created_at::text,
                       au.username,
                       STRING_AGG(DISTINCT c.model, ', ' ORDER BY c.model) AS models,
                       au.utm_source,
                       au.referrer_source,
                       (SELECT COUNT(*) FROM ev_log el JOIN car c2 ON el.car_id = c2.id WHERE c2.user_id = au.id) AS evlog_count,
                       (SELECT STRING_AGG(DISTINCT el.data_source, ', ' ORDER BY el.data_source)
                        FROM ev_log el JOIN car c2 ON el.car_id = c2.id WHERE c2.user_id = au.id) AS data_sources
                FROM app_user au
                LEFT JOIN car c ON au.id = c.user_id
                WHERE au.is_seed_data IS FALSE
                GROUP BY au.id, au.email, au.created_at, au.username, au.email_verified, au.utm_source, au.referrer_source
                ORDER BY au.created_at DESC
                """).getResultList();

        return rows.stream()
                .map(r -> new AdminUserRow(
                        (String) r[0],
                        r[1] != null ? r[1].toString() : null,
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        (String) r[5],
                        r[6] != null ? ((Number) r[6]).longValue() : 0L,
                        (String) r[7]
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<AdminUserGrowthRow> getUserGrowth() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT DATE(created_at)::text AS day,
                       COUNT(*) AS new_users,
                       SUM(COUNT(*)) OVER (ORDER BY DATE(created_at)) AS cumulative_users
                FROM app_user
                WHERE is_seed_data = false
                GROUP BY DATE(created_at)
                ORDER BY day ASC
                """).getResultList();

        return rows.stream()
                .map(r -> new AdminUserGrowthRow(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        ((Number) r[2]).longValue()
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<AdminChargingActivityRow> getChargingActivity() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT DATE(logged_at)::text                      AS tag,
                       COUNT(*)                                   AS anzahl_ladevorgaenge,
                       COALESCE(SUM(kwh_charged), 0)              AS kwh_gesamt,
                       COALESCE(SUM(cost_eur), 0)                 AS kosten_eur_gesamt,
                       STRING_AGG(DISTINCT data_source, ', ')     AS data_sources,
                       COALESCE(SUM(charge_duration_minutes), 0)  AS dauer_minuten_gesamt,
                       COALESCE(ROUND(AVG(kwh_charged), 2), 0)    AS kwh_durchschnitt,
                       COALESCE(ROUND(AVG(cost_eur), 2), 0)       AS kosten_eur_durchschnitt,
                       COALESCE(ROUND(AVG(charge_duration_minutes), 0), 0) AS dauer_minuten_durchschnitt
                FROM ev_log
                WHERE logged_at IS NOT NULL
                GROUP BY DATE(logged_at)
                ORDER BY tag ASC
                """).getResultList();

        return rows.stream()
                .map(r -> new AdminChargingActivityRow(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        r[2] != null ? new BigDecimal(r[2].toString()) : BigDecimal.ZERO,
                        r[3] != null ? new BigDecimal(r[3].toString()) : BigDecimal.ZERO,
                        (String) r[4],
                        r[5] != null ? ((Number) r[5]).longValue() : 0L,
                        r[6] != null ? new BigDecimal(r[6].toString()) : BigDecimal.ZERO,
                        r[7] != null ? new BigDecimal(r[7].toString()) : BigDecimal.ZERO,
                        r[8] != null ? ((Number) r[8]).longValue() : 0L
                ))
                .toList();
    }
}
