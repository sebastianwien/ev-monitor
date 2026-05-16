package com.evmonitor.application.tessie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link TessieProcessorService} against a real PostgreSQL
 * (via Testcontainers + Flyway). Validates Postgres-specific aspects that H2
 * cannot reproduce: jsonb extraction, window-function merges, FILTER clauses.
 *
 * Auto-skips when Docker is not available - matches the project pattern used by
 * UserDeletionCascadeTest / LeaderboardQueryRepositoryTest.
 *
 * Coverage:
 *  - charge JSON -> ev_log row (data_source=TESSIE, AT_VEHICLE, kwh_at_vehicle)
 *  - public/private classification: SuC and AC>11kW => public (geohash 7),
 *    AC<=11kW => private (geohash 6)
 *  - 6-min same-location merge of consecutive charges
 *  - drive JSON -> ev_trip row (data_source=TESSIE, status=COMPLETED, geohash 6)
 *  - micro-trip filter (< 0.5 km dropped)
 *  - route_type from weighted average speed
 *  - ownership check (foreign carId throws)
 *  - idempotent rerun (processed flag prevents double-insert)
 */
@Disabled("Local: re-enable to verify Postgres-specific SQL against a real container. " +
        "Skipped by default to match the project pattern (UserDeletionCascadeTest, LeaderboardQueryRepositoryTest) - " +
        "Testcontainers' bundled docker-java is older than current Docker Desktop API and does not skip cleanly via disabledWithoutDocker.")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class TessieProcessorServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.task.scheduling.pool.size", () -> "0");
    }

    @Autowired
    private TessieProcessorService processor;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID carId;
    private final String vin = "5YJ3E7EAXKF000001";

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        userId = UUID.randomUUID();
        carId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        jdbc.update("""
                INSERT INTO app_user (id, email, auth_provider, role, username,
                    email_verified, is_seed_data, email_notifications_enabled,
                    leaderboard_visible, is_premium, referral_reward_given, trial_used,
                    country, registration_locale, created_at, updated_at)
                VALUES (?, ?, 'LOCAL', 'USER', ?, true, false, false,
                        false, false, false, false, 'DE', 'de', ?, ?)
                """,
                userId, "tessie-it-" + nano + "@example.com", "tessie-it-" + nano,
                Timestamp.valueOf(now), Timestamp.valueOf(now));

        jdbc.update("""
                INSERT INTO car (id, user_id, model, manufacture_year, license_plate,
                    battery_capacity_kwh, status, is_primary, image_public, is_business_car, has_heat_pump,
                    created_at, updated_at)
                VALUES (?, ?, 'MODEL_3', 2023, ?, 75.00, 'ACTIVE', true, false, false, false, ?, ?)
                """,
                carId, userId, "TST-" + (nano % 1000),
                Timestamp.valueOf(now), Timestamp.valueOf(now));
    }

    @Test
    void processForCar_classifiesAndMergesCharges() {
        long t1 = 1700000000L;
        long t2 = 1700100000L;
        long t3 = 1700200000L;

        insertCharge(1L, """
                {"id":1,"started_at":%d,"ended_at":%d,"energy_added":40.0,
                 "starting_battery":20,"ending_battery":75,"odometer":62000,
                 "latitude":52.50,"longitude":13.40,
                 "is_supercharger":true,"is_fast_charger":false,"charger_power":150}
                """.formatted(t1, t1 + 1800));

        insertCharge(2L, """
                {"id":2,"started_at":%d,"ended_at":%d,"energy_added":18.0,
                 "starting_battery":40,"ending_battery":70,"odometer":62100,
                 "latitude":50.94,"longitude":6.96,
                 "is_supercharger":false,"is_fast_charger":false,"charger_power":3.6}
                """.formatted(t2, t2 + 18000));

        insertCharge(3L, """
                {"id":3,"started_at":%d,"ended_at":%d,"energy_added":11.0,
                 "starting_battery":50,"ending_battery":65,"odometer":62300,
                 "latitude":48.14,"longitude":11.58,
                 "is_supercharger":false,"is_fast_charger":false,"charger_power":22.0}
                """.formatted(t3, t3 + 1800));

        var result = processor.processForCar(userId, vin, carId);
        assertEquals(3, result.evLogsCreated());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT geohash, is_public_charging, charging_type, kwh_at_vehicle,
                       data_source, measurement_type
                FROM ev_log WHERE car_id = ? ORDER BY logged_at
                """, carId);

        assertEquals("DC", rows.get(0).get("charging_type"));
        assertEquals(Boolean.TRUE, rows.get(0).get("is_public_charging"));
        assertEquals(7, ((String) rows.get(0).get("geohash")).length());
        assertEquals("TESSIE", rows.get(0).get("data_source"));
        assertEquals("AT_VEHICLE", rows.get(0).get("measurement_type"));
        assertEquals(0, ((BigDecimal) rows.get(0).get("kwh_at_vehicle")).compareTo(new BigDecimal("40.00")));

        assertEquals("AC", rows.get(1).get("charging_type"));
        assertEquals(Boolean.FALSE, rows.get(1).get("is_public_charging"));
        assertEquals(6, ((String) rows.get(1).get("geohash")).length());

        assertEquals("AC", rows.get(2).get("charging_type"));
        assertEquals(Boolean.TRUE, rows.get(2).get("is_public_charging"));
        assertEquals(7, ((String) rows.get(2).get("geohash")).length());

        Long unprocessed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tessie_raw_imports WHERE user_id = ? AND vin = ? AND processed = false",
                Long.class, userId, vin);
        assertEquals(0L, unprocessed);
    }

    @Test
    void processForCar_mergesShortGapChargesAtSameLocation() {
        long t = 1700300000L;
        insertCharge(10L, """
                {"id":10,"started_at":%d,"ended_at":%d,"energy_added":5.0,
                 "starting_battery":40,"ending_battery":48,"odometer":63000,
                 "latitude":52.501,"longitude":13.402,
                 "is_supercharger":false,"is_fast_charger":false,"charger_power":11.0}
                """.formatted(t, t + 1800));
        insertCharge(11L, """
                {"id":11,"started_at":%d,"ended_at":%d,"energy_added":7.0,
                 "starting_battery":48,"ending_battery":58,"odometer":63000,
                 "latitude":52.5012,"longitude":13.4015,
                 "is_supercharger":false,"is_fast_charger":false,"charger_power":11.0}
                """.formatted(t + 1800 + 300, t + 1800 + 300 + 1800));

        var result = processor.processForCar(userId, vin, carId);
        assertEquals(1, result.evLogsCreated());

        BigDecimal kwh = jdbc.queryForObject(
                "SELECT kwh_at_vehicle FROM ev_log WHERE car_id = ?", BigDecimal.class, carId);
        assertEquals(0, kwh.compareTo(new BigDecimal("12.00")));
    }

    @Test
    void processForCar_classifiesDrivesByRouteTypeAndFiltersMicroTrips() {
        long t1 = 1700400000L;
        long t2 = 1700500000L;
        long t3 = 1700600000L;

        insertDrive(20L, """
                {"id":20,"started_at":%d,"ended_at":%d,"energy_used":10.0,
                 "starting_battery":80,"ending_battery":65,
                 "starting_odometer":40000,"ending_odometer":40031,"odometer_distance":31.069,
                 "starting_latitude":52.50,"starting_longitude":13.40,
                 "ending_latitude":52.85,"ending_longitude":13.95,
                 "average_outside_temperature":15.5,"average_speed":100,"max_speed":135}
                """.formatted(t1, t1 + 1800));

        insertDrive(21L, """
                {"id":21,"started_at":%d,"ended_at":%d,"energy_used":1.5,
                 "starting_battery":65,"ending_battery":62,
                 "starting_odometer":40031,"ending_odometer":40034,"odometer_distance":3.107,
                 "starting_latitude":52.85,"starting_longitude":13.95,
                 "ending_latitude":52.86,"ending_longitude":13.96,
                 "average_outside_temperature":15.0,"average_speed":30}
                """.formatted(t2, t2 + 600));

        insertDrive(22L, """
                {"id":22,"started_at":%d,"ended_at":%d,"energy_used":0.1,
                 "starting_battery":62,"ending_battery":62,
                 "starting_odometer":40034,"ending_odometer":40034.2,"odometer_distance":0.187,
                 "starting_latitude":52.86,"starting_longitude":13.96,
                 "ending_latitude":52.86,"ending_longitude":13.96,
                 "average_outside_temperature":14.0,"average_speed":10}
                """.formatted(t3, t3 + 60));

        var result = processor.processForCar(userId, vin, carId);
        assertEquals(2, result.evTripsCreated(), "Micro-trip below 0.5 km is filtered");

        List<String> routeTypes = jdbc.queryForList(
                "SELECT route_type FROM ev_trip WHERE car_id = ? ORDER BY trip_started_at",
                String.class, carId);
        assertEquals(List.of("HIGHWAY", "CITY"), routeTypes);

        Map<String, Object> firstTrip = jdbc.queryForMap(
                "SELECT location_start_geohash, location_end_geohash, data_source, status, " +
                        "avg_speed_kmh, max_speed_kmh FROM ev_trip WHERE car_id = ? " +
                        "ORDER BY trip_started_at LIMIT 1",
                carId);
        assertEquals(6, ((String) firstTrip.get("location_start_geohash")).length());
        assertEquals(6, ((String) firstTrip.get("location_end_geohash")).length());
        assertEquals("TESSIE", firstTrip.get("data_source"));
        assertEquals("COMPLETED", firstTrip.get("status"));
        // Drive 20 lieferte average_speed=100 und max_speed=135 - beides muss persistiert sein.
        assertEquals(0, ((BigDecimal) firstTrip.get("avg_speed_kmh"))
                .compareTo(new BigDecimal("100.00")));
        assertEquals(0, ((BigDecimal) firstTrip.get("max_speed_kmh"))
                .compareTo(new BigDecimal("135.00")));

        // Drive 21 hat kein max_speed-Feld - bleibt null, avg_speed_kmh wird gesetzt.
        Map<String, Object> secondTrip = jdbc.queryForMap(
                "SELECT avg_speed_kmh, max_speed_kmh FROM ev_trip WHERE car_id = ? " +
                        "ORDER BY trip_started_at OFFSET 1 LIMIT 1",
                carId);
        assertNotNull(secondTrip.get("avg_speed_kmh"));
        assertNull(secondTrip.get("max_speed_kmh"));
    }

    @Test
    void processForCar_throwsWhenCarBelongsToDifferentUser() {
        UUID otherUserId = UUID.randomUUID();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.processForCar(otherUserId, vin, carId));
        assertTrue(ex.getMessage().toLowerCase().contains("not belong"));
    }

    @Test
    void processForCar_isIdempotentOnRerun() {
        long t = 1700700000L;
        insertCharge(30L, """
                {"id":30,"started_at":%d,"ended_at":%d,"energy_added":12.0,
                 "starting_battery":40,"ending_battery":58,"odometer":64000,
                 "latitude":52.50,"longitude":13.40,
                 "is_supercharger":false,"is_fast_charger":false,"charger_power":11.0}
                """.formatted(t, t + 1800));

        processor.processForCar(userId, vin, carId);
        var second = processor.processForCar(userId, vin, carId);

        assertEquals(0, second.evLogsCreated());
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ev_log WHERE car_id = ? AND data_source = 'TESSIE'",
                Long.class, carId);
        assertEquals(1L, count);
    }

    private void insertCharge(long tessieId, String json) {
        insertRaw(tessieId, "charge", json);
    }

    private void insertDrive(long tessieId, String json) {
        insertRaw(tessieId, "drive", json);
    }

    private void insertRaw(long tessieId, String type, String json) {
        long startedAt = extractStartedAt(json);
        jdbc.update("""
                INSERT INTO tessie_raw_imports (user_id, vin, type, tessie_id, recorded_at, raw, processed)
                VALUES (?, ?, ?, ?, to_timestamp(?), ?::jsonb, false)
                """,
                userId, vin, type, tessieId, startedAt, json);
    }

    private static long extractStartedAt(String json) {
        int idx = json.indexOf("\"started_at\"");
        int colon = json.indexOf(':', idx);
        int comma = json.indexOf(',', colon);
        return Long.parseLong(json.substring(colon + 1, comma).trim());
    }
}
