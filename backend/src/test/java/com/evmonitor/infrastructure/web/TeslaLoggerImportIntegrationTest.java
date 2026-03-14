package com.evmonitor.infrastructure.web;

import com.evmonitor.application.spritmonitor.ImportResult;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TeslaLoggerImportController.
 *
 * Tests CSV and JSON import, validation, duplicate detection, and auth.
 */
class TeslaLoggerImportIntegrationTest extends AbstractIntegrationTest {

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("teslalogger-" + System.nanoTime() + "@ev-monitor.net");
        car  = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    // ── CSV import ────────────────────────────────────────────────────────────

    @Test
    void csvImport_importsValidRows() {
        String csv = """
                date,odometer_km,kwh,soc_before,soc_after,cost_eur,location,duration_min
                2025-08-20T10:56:48,12345,24.5,45,80,8.50,Tesla Supercharger Wien,35
                2025-09-01T14:22:00,13102,18.2,30,72,,,28
                """;

        ResponseEntity<ImportResult> response = post(csv, "csv");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getImported());
        assertEquals(0, response.getBody().getSkipped());
        assertTrue(response.getBody().getErrors().isEmpty());

        List<EvLog> logs = evLogRepository.findAllByCarId(car.getId());
        assertEquals(2, logs.size());
        // First log: odometer + kwh + soc_after
        EvLog first = logs.stream()
                .filter(l -> l.getOdometerKm() == 12345)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("24.5").compareTo(first.getKwhCharged()));
        assertEquals(80, first.getSocAfterChargePercent());
        assertEquals(45, first.getSocBeforeChargePercent());
        assertEquals(DataSource.TESLA_LOGGER_IMPORT, first.getDataSource());
        assertTrue(first.isIncludeInStatistics());
    }

    @Test
    void csvImport_socBefore_storedSeparatelyFromSocAfter() {
        String csv = """
                date,odometer_km,kwh,soc_before,soc_after
                2025-08-20T10:00:00,10000,20.0,40,80
                """;

        ResponseEntity<ImportResult> response = post(csv, "csv");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getImported());

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertEquals(80, log.getSocAfterChargePercent());
        assertEquals(40, log.getSocBeforeChargePercent());
    }

    @Test
    void csvImport_optionalFields_maxPowerAndTemperature() {
        String csv = """
                date,odometer_km,kwh,soc_after,max_charging_power_kw,temperature_celsius
                2025-08-20T10:00:00,10000,20.0,80,150.0,23.5
                """;

        post(csv, "csv");

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertEquals(0, new BigDecimal("150.0").compareTo(log.getMaxChargingPowerKw()));
        assertEquals(23.5, log.getTemperatureCelsius());
    }

    @Test
    void csvImport_latLon_computesGeohash() {
        String csv = """
                date,odometer_km,kwh,soc_after,location
                2025-08-20T10:00:00,10000,20.0,80,48.2082 16.3738
                """;

        post(csv, "csv");

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertNotNull(log.getGeohash());
        assertEquals(5, log.getGeohash().length());
    }

    @Test
    void csvImport_placeName_geohashIsNull() {
        String csv = """
                date,odometer_km,kwh,soc_after,location
                2025-08-20T10:00:00,10000,20.0,80,IONITY Frankfurt
                """;

        post(csv, "csv");

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertNull(log.getGeohash());  // no geocoding for place names
    }

    // ── JSON import ───────────────────────────────────────────────────────────

    @Test
    void jsonImport_importsValidRows() {
        String json = """
                [
                  {"date":"2025-08-20T10:56:48","odometer_km":12345,"kwh":24.5,"soc_after":80},
                  {"date":"2025-09-01T14:22:00","odometer_km":13102,"kwh":18.2,"soc_before":30,"soc_after":72}
                ]
                """;

        ResponseEntity<ImportResult> response = post(json, "json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getImported());
    }

    // ── Duplicate detection ───────────────────────────────────────────────────

    @Test
    void csvImport_duplicateWithin1h_isSkipped() {
        // Pre-existing log at 10:56
        evLogRepository.save(EvLog.createNewWithSource(
                car.getId(), new BigDecimal("24.5"), null, null, null,
                12345, null, 80, LocalDateTime.parse("2025-08-20T10:56:48"),
                DataSource.TESLA_LOGGER_IMPORT, com.evmonitor.domain.ChargingType.UNKNOWN, null
        ));

        // Import same session (exact same time)
        String csv = """
                date,odometer_km,kwh,soc_after
                2025-08-20T10:56:48,12345,24.5,80
                """;

        ResponseEntity<ImportResult> response = post(csv, "csv");

        assertEquals(1, response.getBody().getSkipped());
        assertEquals(0, response.getBody().getImported());
    }

    @Test
    void csvImport_sameCarDifferentTime_notDuplicate() {
        evLogRepository.save(EvLog.createNewWithSource(
                car.getId(), new BigDecimal("24.5"), null, null, null,
                12345, null, 80, LocalDateTime.parse("2025-08-20T10:56:48"),
                DataSource.TESLA_LOGGER_IMPORT, com.evmonitor.domain.ChargingType.UNKNOWN, null
        ));

        // 3 hours later — outside ±1h window
        String csv = """
                date,odometer_km,kwh,soc_after
                2025-08-20T14:00:00,13000,30.0,85
                """;

        ResponseEntity<ImportResult> response = post(csv, "csv");

        assertEquals(1, response.getBody().getImported());
    }

    @Test
    void csvImport_differentOdometerWithin1h_notDuplicate() {
        // Two real charging sessions 30 minutes apart with different odometer readings
        String csv = """
                date,odometer_km,kwh,soc_before,soc_after
                2025-08-20T10:57:00,12345,38.64,18,80
                2025-08-20T10:27:00,12100,13.22,5,26
                """;

        ResponseEntity<ImportResult> response = post(csv, "csv");

        assertEquals(2, response.getBody().getImported());
        assertEquals(0, response.getBody().getSkipped());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void csvImport_missingDate_isSkipped() {
        String csv = """
                date,odometer_km,kwh,soc_after
                ,12345,24.5,80
                """;

        ResponseEntity<ImportResult> response = post(csv, "csv");

        assertEquals(1, response.getBody().getSkipped());
        assertFalse(response.getBody().getErrors().isEmpty());
    }

    @Test
    void csvImport_missingSocBothFields_isSkipped() {
        String csv = """
                date,odometer_km,kwh,soc_before,soc_after
                2025-08-20T10:00:00,12345,24.5,,
                """;

        ResponseEntity<ImportResult> response = post(csv, "csv");

        assertEquals(1, response.getBody().getSkipped());
    }

    @Test
    void csvImport_zeroKwh_isSkipped() {
        String csv = """
                date,odometer_km,kwh,soc_after
                2025-08-20T10:00:00,12345,0,80
                """;

        ResponseEntity<ImportResult> response = post(csv, "csv");

        assertEquals(1, response.getBody().getSkipped());
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Test
    void import_unauthenticated_returns401() {
        Map<String, String> body = Map.of("carId", car.getId().toString(), "format", "csv", "data", "x");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/import/tesla-logger", body, String.class);
        // Spring Security returns 403 for unauthenticated requests without a challenge endpoint
        assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode());
    }

    @Test
    void import_otherUsersCar_returns400() {
        User other = createAndSaveUser("other-teslalogger@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);

        String csv = "date,odometer_km,kwh,soc_after\n2025-08-20T10:00:00,12345,24.5,80\n";

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/tesla-logger",
                HttpMethod.POST,
                createAuthRequest(Map.of("carId", otherCar.getId(), "format", "csv", "data", csv),
                        user.getId(), user.getEmail()),
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<ImportResult> post(String data, String format) {
        Map<String, Object> body = Map.of(
                "carId", car.getId(),
                "format", format,
                "data", data
        );
        return restTemplate.exchange(
                "/api/import/tesla-logger",
                HttpMethod.POST,
                createAuthRequest(body, user.getId(), user.getEmail()),
                ImportResult.class
        );
    }
}
