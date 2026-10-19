package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * DB-Seite des SEO-Tabs: Registrierungen je Landing-Seite aus app_user (First-Touch-Attribution) und die
 * Datenbasis je Modell. Zählt nur Konten ohne Seed-Daten; "organisch" heißt: Verweis von einer Google-Domain
 * ohne bezahltes Medium.
 */
@Repository
public class SeoAttributionRepository {

    public record RegistrationTotals(long registrations, long organic) {
        public static final RegistrationTotals EMPTY = new RegistrationTotals(0, 0);
    }

    /** Konten und davon Trials/Abos je UTM-Tripel (klein geschrieben, Schlüssel {@code source|medium|campaign}). */
    public record UtmTotals(long registrations, long trials) {
    }

    /** Aktive Autos und Ladevorgänge in der Statistik je Modell (Enum-Name), ohne Seed-Nutzer und Gelöschtes. */
    public record DataBasis(long cars, long logs) {
        public static final DataBasis EMPTY = new DataBasis(0, 0);
    }

    private static final String ORGANIC =
            "referrer_source LIKE '%google.%' AND (utm_medium IS NULL OR utm_medium NOT IN ('cpc', 'paid'))";

    private final JdbcTemplate jdbc;

    public SeoAttributionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Long> registrationsByLandingPath(LocalDate from, LocalDate to) {
        Map<String, Long> result = new HashMap<>();
        jdbc.query("SELECT landing_path, count(*) AS n FROM app_user WHERE landing_path IS NOT NULL AND is_seed_data = FALSE "
                        + "AND created_at >= ? AND created_at < ? GROUP BY landing_path",
                rs -> { result.put(rs.getString("landing_path"), rs.getLong("n")); },
                start(from), end(to));
        return result;
    }

    /** Registrierungen und davon organische je erster Seite; Konten ohne bekannte erste Seite fehlen. */
    public Map<String, RegistrationTotals> landingPathTotals(LocalDate from, LocalDate to) {
        Map<String, RegistrationTotals> result = new HashMap<>();
        jdbc.query("SELECT landing_path, count(*) AS n, SUM(CASE WHEN " + ORGANIC + " THEN 1 ELSE 0 END) AS organic "
                        + "FROM app_user WHERE landing_path IS NOT NULL AND is_seed_data = FALSE "
                        + "AND created_at >= ? AND created_at < ? GROUP BY landing_path",
                rs -> { result.put(rs.getString("landing_path"), new RegistrationTotals(rs.getLong("n"), rs.getLong("organic"))); },
                start(from), end(to));
        return result;
    }

    public RegistrationTotals registrationTotals(LocalDate from, LocalDate to) {
        return jdbc.queryForObject("SELECT count(*) AS n, SUM(CASE WHEN " + ORGANIC + " THEN 1 ELSE 0 END) AS organic "
                        + "FROM app_user WHERE is_seed_data = FALSE AND created_at >= ? AND created_at < ?",
                (rs, i) -> new RegistrationTotals(rs.getLong("n"), rs.getLong("organic")),
                start(from), end(to));
    }

    public Map<String, UtmTotals> registrationsByUtm(LocalDate from, LocalDate to) {
        Map<String, UtmTotals> result = new HashMap<>();
        jdbc.query("SELECT lower(utm_source) AS s, lower(coalesce(utm_medium, '')) AS m, lower(coalesce(utm_campaign, '')) AS c, "
                        + "count(*) AS n, SUM(CASE WHEN trial_used = TRUE OR subscription_tier <> 'NONE' THEN 1 ELSE 0 END) AS trials "
                        + "FROM app_user WHERE utm_source IS NOT NULL AND is_seed_data = FALSE AND created_at >= ? AND created_at < ? "
                        + "GROUP BY lower(utm_source), lower(coalesce(utm_medium, '')), lower(coalesce(utm_campaign, ''))",
                rs -> { result.put(utmKey(rs.getString("s"), rs.getString("m"), rs.getString("c")),
                        new UtmTotals(rs.getLong("n"), rs.getLong("trials"))); },
                start(from), end(to));
        return result;
    }

    public Map<String, DataBasis> dataBasisByModel() {
        Map<String, DataBasis> result = new HashMap<>();
        jdbc.query("""
                        SELECT c.model, count(DISTINCT c.id) AS cars, count(l.id) AS logs FROM car c
                        JOIN app_user u ON u.id = c.user_id AND u.is_seed_data = FALSE
                        LEFT JOIN ev_log l ON l.car_id = c.id AND l.deleted_at IS NULL AND l.include_in_statistics = TRUE
                        WHERE c.deleted_at IS NULL AND c.model IS NOT NULL GROUP BY c.model""",
                rs -> { result.put(rs.getString("model"), new DataBasis(rs.getLong("cars"), rs.getLong("logs"))); });
        return result;
    }

    public static String utmKey(String source, String medium, String campaign) {
        return lower(source) + "|" + lower(medium) + "|" + lower(campaign);
    }

    private static String lower(String v) {
        return v == null ? "" : v.trim().toLowerCase();
    }

    private static Timestamp start(LocalDate day) {
        return Timestamp.valueOf(day.atStartOfDay());
    }

    private static Timestamp end(LocalDate day) {
        return Timestamp.valueOf(day.plusDays(1).atStartOfDay());
    }
}
