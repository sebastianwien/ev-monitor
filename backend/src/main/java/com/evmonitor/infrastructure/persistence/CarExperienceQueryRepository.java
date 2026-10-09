package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.rating.CarExperience;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Erfahrung je Auto für die Fahrzeugbewertung: Anzahl nicht gelöschter Logs und selbst gefahrene
 * Kilometer (höchster minus niedrigster km-Stand). km-Stände von 0 oder kleiner sind Platzhalter
 * aus Importen und zählen nicht.
 */
@Repository
public class CarExperienceQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CarExperienceQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Autos ohne Logs fehlen in der Map. */
    public Map<UUID, CarExperience> findByCarIds(Collection<UUID> carIds) {
        Map<UUID, CarExperience> result = new HashMap<>();
        if (carIds.isEmpty()) return result;
        jdbc.query("""
                SELECT e.car_id,
                       COUNT(*) AS log_count,
                       COALESCE(MAX(CASE WHEN e.odometer_km > 0 THEN e.odometer_km END)
                              - MIN(CASE WHEN e.odometer_km > 0 THEN e.odometer_km END), 0) AS km_driven,
                       MIN(e.logged_at) AS first_logged_at
                FROM ev_log e
                WHERE e.car_id IN (:carIds) AND e.deleted_at IS NULL
                GROUP BY e.car_id
                """, new MapSqlParameterSource("carIds", carIds), rs -> {
            Timestamp first = rs.getTimestamp("first_logged_at");
            result.put(rs.getObject("car_id", UUID.class), new CarExperience(
                    rs.getInt("log_count"),
                    rs.getInt("km_driven"),
                    first == null ? null : first.toLocalDateTime().toLocalDate()));
        });
        return result;
    }

    public CarExperience findByCarId(UUID carId) {
        return findByCarIds(java.util.List.of(carId)).getOrDefault(carId, CarExperience.NONE);
    }
}
