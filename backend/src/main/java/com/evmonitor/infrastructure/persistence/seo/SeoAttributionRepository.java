package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Registrierungen je Landing-Seite aus app_user (First-Touch-Attribution). Zählt angelegte Konten ohne
 * Seed-Daten; "organisch" heißt: Verweis von einer Google-Domain ohne bezahltes Medium.
 */
@Repository
public class SeoAttributionRepository {

    public record RegistrationTotals(long registrations, long organic) {
        public static final RegistrationTotals EMPTY = new RegistrationTotals(0, 0);
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

    public RegistrationTotals registrationTotals(LocalDate from, LocalDate to) {
        return jdbc.queryForObject("SELECT count(*) AS n, SUM(CASE WHEN " + ORGANIC + " THEN 1 ELSE 0 END) AS organic "
                        + "FROM app_user WHERE is_seed_data = FALSE AND created_at >= ? AND created_at < ?",
                (rs, i) -> new RegistrationTotals(rs.getLong("n"), rs.getLong("organic")),
                start(from), end(to));
    }

    private static Timestamp start(LocalDate day) {
        return Timestamp.valueOf(day.atStartOfDay());
    }

    private static Timestamp end(LocalDate day) {
        return Timestamp.valueOf(day.plusDays(1).atStartOfDay());
    }
}
