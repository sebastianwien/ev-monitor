package com.evmonitor.infrastructure.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Aktueller Kilometerstand: der zeitlich neueste Tachostand aus Ladevorgängen und Fahrten, dieselbe
 * Punktmenge wie die Gesamtstrecke in {@code EvLogStatisticsService}. Neuester statt höchster, weil ein
 * alter Tippfehler nach oben sonst dauerhaft stehen bliebe. km-Stände von 0 oder kleiner sind
 * Import-Platzhalter. logged_at ist Wandzeit ohne Zone und wird wie dort als UTC gelesen.
 */
@Repository
public class CarOdometerQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CarOdometerQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** null, wenn das Auto keinen Tachostand hat. */
    public Integer findCurrentOdometerKm(UUID carId) {
        return jdbc.query("""
                SELECT ROUND(odo)::int AS odo FROM (
                    SELECT odometer_km AS odo, logged_at AT TIME ZONE 'UTC' AS ts
                    FROM ev_log
                    WHERE car_id = :carId AND deleted_at IS NULL AND odometer_km > 0
                    UNION ALL
                    SELECT COALESCE(odometer_end_km, odometer_start_km),
                           CASE WHEN odometer_end_km IS NOT NULL AND trip_ended_at IS NOT NULL
                                THEN trip_ended_at ELSE trip_started_at END
                    FROM ev_trip
                    WHERE car_id = :carId AND deleted_at IS NULL
                      AND COALESCE(odometer_end_km, odometer_start_km) > 0
                ) points
                ORDER BY ts DESC
                LIMIT 1
                """, new MapSqlParameterSource("carId", carId),
                rs -> rs.next() ? rs.getInt("odo") : null);
    }
}
