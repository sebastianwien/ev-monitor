package com.evmonitor.application.tessie;

import com.evmonitor.testutil.SharedPostgres;
import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.EvLogSavedEvent;
import com.evmonitor.application.SohAutoDetectEvent;
import com.evmonitor.application.ingest.event.ImportEventOutcome;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.CoinLogRepository;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.route.RouteSketcher;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@RecordApplicationEvents
class TessieProcessorServiceIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        SharedPostgres.registerMigrated(registry);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private TessieProcessorService processor;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private CoinLogRepository coinLogRepository;

    @Autowired
    private ImportEventRepository importEventRepository;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private EvLogRepository evLogRepository;

    @MockitoBean
    private RouteSketcher routeSketcher;

    private UUID userId;
    private UUID carId;
    private final String vin = "5YJ3E7EAXKF000001";

    @BeforeEach
    void setUp() {
        User user = userRepository.save(TestDataBuilder.createTestUser("tessie-it-" + System.nanoTime() + "@example.com"));
        userId = user.getId();
        carId = carRepository.save(TestDataBuilder.createTestCar(userId, CarBrand.CarModel.MODEL_3, new BigDecimal("75.00"))).getId();
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
        assertNull(rows.get(1).get("is_public_charging"), "AC bis 11 kW: öffentlich/daheim unbekannt (R2h)");
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

    /**
     * Regression: TessieClient fetches with distance_format=km, so raw odometer values
     * are already kilometres. The merge SQL must not convert them again.
     */
    @Test
    void processForCar_takesOdometerFromRawAsKilometresWithoutConversion() {
        long t = 1700800000L;
        insertCharge(40L, """
                {"id":40,"started_at":%d,"ended_at":%d,"energy_added":12.0,
                 "starting_battery":40,"ending_battery":58,"odometer":26829,
                 "latitude":52.50,"longitude":13.40,
                 "is_supercharger":false,"is_fast_charger":false,"charger_power":11.0}
                """.formatted(t, t + 1800));

        processor.processForCar(userId, vin, carId);

        Integer odometerKm = jdbc.queryForObject(
                "SELECT odometer_km FROM ev_log WHERE car_id = ? AND data_source = 'TESSIE'",
                Integer.class, carId);
        assertEquals(26829, odometerKm, "Raw odometer is already km - no miles conversion");
    }

    /**
     * Regression: trip odometers and distance come from Tessie in km too. A converted
     * distance_km would inflate every trip by 1.60934 and deflate kWh/100km accordingly.
     */
    @Test
    void processForCar_takesTripOdometerAndDistanceFromRawAsKilometres() {
        long t = 1700900000L;
        insertDrive(41L, """
                {"id":41,"started_at":%d,"ended_at":%d,"energy_used":10.0,
                 "starting_battery":80,"ending_battery":65,
                 "starting_odometer":40000,"ending_odometer":40100,"odometer_distance":100.0,
                 "starting_latitude":52.50,"starting_longitude":13.40,
                 "ending_latitude":52.85,"ending_longitude":13.95,
                 "average_outside_temperature":15.5,"average_speed":100,"max_speed":135}
                """.formatted(t, t + 3600));

        processor.processForCar(userId, vin, carId);

        Map<String, Object> trip = jdbc.queryForMap(
                "SELECT odometer_start_km, odometer_end_km, distance_km FROM ev_trip WHERE car_id = ?",
                carId);
        assertEquals(0, ((BigDecimal) trip.get("odometer_start_km")).compareTo(new BigDecimal("40000")));
        assertEquals(0, ((BigDecimal) trip.get("odometer_end_km")).compareTo(new BigDecimal("40100")));
        assertEquals(0, ((BigDecimal) trip.get("distance_km")).compareTo(new BigDecimal("100")),
                "Raw odometer_distance is already km - no miles conversion");
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

    /**
     * Eine gelöschte Tessie-Ladung zur selben Startzeit blockiert die Anlage wie vor V190 (damals zählte die
     * Unique-Constraint Tombstones mit): vom Nutzer gelöscht heißt gelöscht.
     */
    @Test
    void processForCar_deletedChargeAtSameStart_blocksInsert() {
        long t = 1700800000L;
        String charge = """
                {"id":%d,"started_at":%d,"ended_at":%d,"energy_added":12.0,
                 "starting_battery":40,"ending_battery":58,"odometer":64000,
                 "latitude":52.50,"longitude":13.40,
                 "is_supercharger":false,"is_fast_charger":false,"charger_power":11.0}
                """;
        insertCharge(40L, charge.formatted(40, t, t + 1800));
        processor.processForCar(userId, vin, carId);
        jdbc.update("UPDATE ev_log SET deleted_at = NOW() WHERE car_id = ? AND data_source = 'TESSIE'", carId);
        insertCharge(41L, charge.formatted(41, t, t + 1800));

        var result = processor.processForCar(userId, vin, carId);

        assertEquals(0, result.evLogsCreated());
        Long active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ev_log WHERE car_id = ? AND data_source = 'TESSIE' AND deleted_at IS NULL",
                Long.class, carId);
        assertEquals(0L, active);
    }

    /**
     * Über das Gateway (R2h, bewusst geändert): Uhrzeit auf die Minute, SoH-Erkennung angestoßen, eine Zeile
     * Import-Protokoll, AC bis 11 kW ohne Aussage öffentlich/daheim. Wie bisher: keine Watt, ohne bezahlte
     * Ladung am Ort kein Preis, Temperatur wird nachgeholt (ein EvLogSavedEvent je Ladung).
     */
    @Test
    void processForCar_charge_viaGateway() {
        long t = 1700900005L;   // 2023-11-25T08:13:25Z
        insertCharge(50L, privateAcCharge(50, t));

        processor.processForCar(userId, vin, carId);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT EXTRACT(SECOND FROM logged_at)::int AS sec, cost_eur, kwh_charged, kwh_at_vehicle,
                       is_public_charging, measurement_type
                FROM ev_log WHERE car_id = ? AND data_source = 'TESSIE'
                """, carId);
        assertEquals(0, row.get("sec"));
        assertNull(row.get("cost_eur"));
        assertNull(row.get("kwh_charged"));
        assertEquals(0, new BigDecimal("12.00").compareTo((BigDecimal) row.get("kwh_at_vehicle")));
        assertEquals("AT_VEHICLE", row.get("measurement_type"));
        assertNull(row.get("is_public_charging"));
        assertTrue(coinLogRepository.findAllByUserId(userId).isEmpty());
        assertEquals(1, events.stream(SohAutoDetectEvent.class).count());
        assertEquals(1, events.stream(EvLogSavedEvent.class).count());
        List<ImportEvent> protocol = importEvents();
        assertEquals(1, protocol.size());
        assertEquals("TESSIE", protocol.get(0).getDataSource());
        assertEquals(ImportEventOutcome.IMPORTED, protocol.get(0).getOutcome());
        assertEquals(1, protocol.get(0).getSessionsImported());
    }

    /** Preis vom Ort (R2h, bewusst geändert): eine bezahlte Ladung am selben Ort bepreist die Tessie-Ladung mit. */
    @Test
    void processForCar_charge_isPricedFromSameLocation() {
        evLogRepository.save(EvLog.createNewWithSource(carId, new BigDecimal("30"), new BigDecimal("9.00"), 60,
                        GeoHash.withCharacterPrecision(52.50, 13.40, 6).toBase32(), null, null, null,
                        LocalDateTime.of(2023, 11, 1, 18, 0), DataSource.USER_LOGGED, ChargingType.AC, null)
                .toBuilder().pricePerKwh(new BigDecimal("0.30")).build());
        insertCharge(51L, privateAcCharge(51, 1700900005L));

        processor.processForCar(userId, vin, carId);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT cost_eur, price_per_kwh
                FROM ev_log WHERE car_id = ? AND data_source = 'TESSIE'
                """, carId);
        assertNotNull(row.get("cost_eur"));
        assertEquals(0, new BigDecimal("0.30").compareTo((BigDecimal) row.get("price_per_kwh")));
    }

    /**
     * Fahrten über das Gateway (R2h, bewusst geändert): nach dem Commit die Routenlinie aus Start- und Zielort,
     * je Fahrt eine Zeile Import-Protokoll. Streckenart weiter aus der Durchschnittsgeschwindigkeit.
     */
    @Test
    void processForCar_drive_getsRouteSketch_andImportEvent() {
        long t = 1701000000L;
        insertDrive(60L, """
                {"id":60,"started_at":%d,"ended_at":%d,"starting_battery":80,"ending_battery":70,
                 "starting_odometer":1000.0,"ending_odometer":1030.0,"odometer_distance":30.0,
                 "energy_used":5.0,"starting_latitude":52.50,"starting_longitude":13.40,
                 "ending_latitude":52.60,"ending_longitude":13.60,"average_outside_temperature":12.0,
                 "average_speed":100,"max_speed":130}
                """.formatted(t, t + 1800));

        processor.processForCar(userId, vin, carId);

        Map<String, Object> trip = jdbc.queryForMap(
                "SELECT id, location_start_geohash, location_end_geohash, route_type, user_created FROM ev_trip "
                        + "WHERE car_id = ? AND data_source = 'TESSIE'", carId);
        verify(routeSketcher).sketchTrip((UUID) trip.get("id"),
                (String) trip.get("location_start_geohash"), (String) trip.get("location_end_geohash"));
        assertEquals(Boolean.FALSE, trip.get("user_created"));
        assertEquals(1, importEvents().stream().filter(e -> e.getTripsImported() == 1).count());
    }

    /** Private AC-Ladung in Berlin: 12 kWh in 2 Stunden (6 kW), Start zur Sekunde {@code startedAt}. */
    private static String privateAcCharge(long tessieId, long startedAt) {
        return """
                {"id":%d,"started_at":%d,"ended_at":%d,"energy_added":12.0,
                 "starting_battery":40,"ending_battery":58,"odometer":64000,
                 "latitude":52.50,"longitude":13.40,
                 "is_supercharger":false,"is_fast_charger":false,"charger_power":11.0}
                """.formatted(tessieId, startedAt, startedAt + 7200);
    }

    private List<ImportEvent> importEvents() {
        return importEventRepository.findAll().stream().filter(e -> userId.equals(e.getUserId())).toList();
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
