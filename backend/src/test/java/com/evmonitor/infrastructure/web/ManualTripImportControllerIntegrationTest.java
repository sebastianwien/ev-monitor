package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ManualTripImportController (POST /api/import/trips).
 *
 * Covers: auth, ownership, request validation, CSV/JSON parsing, duplicate
 * handling (against existing trips and within one file), consumption estimate,
 * and that no coins are awarded.
 */
class ManualTripImportControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EvTripRepository evTripRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("tripimport-" + System.nanoTime() + "@ev-monitor.net");
        car  = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    // ── Auth & Ownership ──────────────────────────────────────────────────────

    @Test
    void unauthenticated_returns401or403() {
        Map<String, Object> body = Map.of("carId", car.getId(), "format", "csv", "data", "x");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/import/trips", body, String.class);
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
    }

    @Test
    void otherUsersCar_returns403() {
        User other = createAndSaveUser("other-tripimport-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/trips", HttpMethod.POST,
                createAuthRequest(Map.of("carId", otherCar.getId(), "format", "csv", "data", validCsv()),
                        user.getId(), user.getEmail()),
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(evTripRepository.findAllByCarIdAndDeletedAtIsNull(otherCar.getId()).isEmpty());
    }

    // ── Request validation ────────────────────────────────────────────────────

    @Test
    void missingCarId_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/trips", HttpMethod.POST,
                createAuthRequest(Map.of("format", "csv", "data", validCsv()), user.getId(), user.getEmail()),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void emptyData_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/trips", HttpMethod.POST,
                createAuthRequest(Map.of("carId", car.getId(), "format", "csv", "data", "  "), user.getId(), user.getEmail()),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void invalidFormat_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/trips", HttpMethod.POST,
                createAuthRequest(Map.of("carId", car.getId(), "format", "xml", "data", validCsv()), user.getId(), user.getEmail()),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── CSV import ────────────────────────────────────────────────────────────

    @Test
    void csvImport_validRows_importedWithAllFields() {
        String csv = """
                started_at,ended_at,distance_km,odometer_start_km,odometer_end_km,soc_start,soc_end,route_type
                2025-08-31T15:07:14+02:00,2025-08-31T15:52:00+02:00,41.3,7852,7893.3,80,62,COMBINED
                2025-09-01T08:00:00+02:00,2025-09-01T08:30:00+02:00,20,7893.3,7913.3,62,55,CITY
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().imported());
        assertEquals(0, response.getBody().skipped());
        assertEquals(0, response.getBody().errors());

        List<EvTrip> trips = evTripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId());
        assertEquals(2, trips.size());
        EvTrip first = trips.stream()
                .filter(t -> t.getTripStartedAt().isEqual(OffsetDateTime.parse("2025-08-31T15:07:14+02:00")))
                .findFirst().orElseThrow();
        assertEquals(EvTrip.DATA_SOURCE_API_UPLOAD, first.getDataSource());
        assertTrue(first.isUserCreated());
        assertEquals("COMPLETED", first.getStatus());
        assertEquals(0, new BigDecimal("41.3").compareTo(first.getDistanceKm()));
        assertEquals(0, new BigDecimal("7852").compareTo(first.getOdometerStartKm()));
        assertEquals(0, new BigDecimal("7893.3").compareTo(first.getOdometerEndKm()));
        assertEquals(0, new BigDecimal("80").compareTo(first.getSocStart()));
        assertEquals(0, new BigDecimal("62").compareTo(first.getSocEnd()));
        assertEquals("COMBINED", first.getRouteType());
        assertNotNull(first.getEstimatedConsumedKwh(), "SoC delta must produce a consumption estimate");
    }

    @Test
    void csvImport_missingStartedAt_countedAsError() {
        String csv = """
                started_at,ended_at,distance_km
                ,2025-08-31T15:52:00+02:00,41.3
                2025-09-01T08:00:00+02:00,2025-09-01T08:30:00+02:00,20
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(1, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
    }

    @Test
    void csvImport_endedBeforeStarted_countedAsError() {
        String csv = """
                started_at,ended_at,distance_km
                2025-08-31T16:00:00+02:00,2025-08-31T15:00:00+02:00,41.3
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(0, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
        assertTrue(evTripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId()).isEmpty());
    }

    @Test
    void csvImport_invalidDate_countedAsError() {
        String csv = """
                started_at,ended_at,distance_km
                31.08.2025 15:07,31.08.2025 15:52,41.3
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(0, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
    }

    @Test
    void csvImport_outOfRangeSoc_countedAsError() {
        String csv = """
                started_at,ended_at,distance_km,soc_start,soc_end
                2025-08-31T15:07:14+02:00,2025-08-31T15:52:00+02:00,41.3,180,62
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(0, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
    }

    @Test
    void csvImport_withoutDistanceAndOdometer_countedAsError() {
        String csv = """
                started_at,ended_at,soc_start,soc_end
                2025-08-31T15:07:14+02:00,2025-08-31T15:52:00+02:00,80,62
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(0, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
        assertTrue(evTripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId()).isEmpty());
    }

    @Test
    void csvImport_odometerOnly_derivesDistance() {
        String csv = """
                started_at,ended_at,odometer_start_km,odometer_end_km
                2025-08-31T15:07:14+02:00,2025-08-31T15:52:00+02:00,7852,7893.3
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(1, response.getBody().imported());
        EvTrip saved = evTripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId()).get(0);
        assertEquals(0, new BigDecimal("41.3").compareTo(saved.getDistanceKm()));
    }

    @Test
    void csvImport_odometerEndBeforeStart_countedAsError() {
        String csv = """
                started_at,ended_at,odometer_start_km,odometer_end_km
                2025-08-31T15:07:14+02:00,2025-08-31T15:52:00+02:00,7893,7852
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(0, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
    }

    // ── Duplicates ────────────────────────────────────────────────────────────

    @Test
    void reimportSameFile_skipsAllRows() {
        ResponseEntity<ImportApiResult> first = post(validCsv(), "csv");
        assertEquals(1, first.getBody().imported());

        ResponseEntity<ImportApiResult> second = post(validCsv(), "csv");

        assertEquals(0, second.getBody().imported());
        assertEquals(1, second.getBody().skipped());
        assertEquals(0, second.getBody().errors());
        assertEquals(1, evTripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId()).size());
    }

    @Test
    void existingTelemetryTripWithSameStart_isSkipped() {
        OffsetDateTime startedAt = OffsetDateTime.parse("2025-08-31T15:07:14+02:00");
        evTripRepository.save(EvTrip.builder()
                .carId(car.getId()).userId(user.getId())
                .dataSource(EvTrip.DATA_SOURCE_TESLA_LIVE)
                .tripStartedAt(startedAt).tripEndedAt(startedAt.plusMinutes(45))
                .status("COMPLETED").userCreated(false)
                .build());

        ResponseEntity<ImportApiResult> response = post(validCsv(), "csv");

        assertEquals(0, response.getBody().imported());
        assertEquals(1, response.getBody().skipped());
        assertEquals(1, evTripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId()).size());
    }

    @Test
    void duplicateStartWithinFile_secondRowSkipped() {
        String csv = """
                started_at,ended_at,distance_km
                2025-08-31T15:07:14+02:00,2025-08-31T15:52:00+02:00,41.3
                2025-08-31T13:07:14Z,2025-08-31T14:00:00Z,41.3
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(1, response.getBody().imported());
        assertEquals(1, response.getBody().skipped());
    }

    // ── JSON import ───────────────────────────────────────────────────────────

    @Test
    void jsonImport_validArray_imported() {
        String json = """
                [
                  {"started_at": "2025-08-31T15:07:14+02:00", "ended_at": "2025-08-31T15:52:00+02:00",
                   "distance_km": 41.3, "soc_start": 80, "soc_end": 62, "route_type": "HIGHWAY"},
                  {"started_at": "2025-09-01T08:00:00+02:00", "ended_at": "2025-09-01T08:30:00+02:00", "distance_km": 12}
                ]
                """;

        ResponseEntity<ImportApiResult> response = post(json, "json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().imported());
        assertEquals(0, response.getBody().errors());
    }

    @Test
    void jsonImport_invalidJson_returns200WithError() {
        ResponseEntity<ImportApiResult> response = post("{not json", "json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
    }

    // ── Coins ─────────────────────────────────────────────────────────────────

    @Test
    void import_awardsNoCoins() {
        int coinsBefore = coinLogRepository.findAllByUserId(user.getId()).size();

        post(validCsv(), "csv");

        List<CoinLog> coins = coinLogRepository.findAllByUserId(user.getId());
        assertEquals(coinsBefore, coins.size(), "Trip import must not award coins");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String validCsv() {
        return """
                started_at,ended_at,distance_km,soc_start,soc_end
                2025-08-31T15:07:14+02:00,2025-08-31T15:52:00+02:00,41.3,80,62
                """;
    }

    private ResponseEntity<ImportApiResult> post(String data, String format) {
        return restTemplate.exchange(
                "/api/import/trips", HttpMethod.POST,
                createAuthRequest(Map.of("carId", car.getId(), "format", format, "data", data),
                        user.getId(), user.getEmail()),
                ImportApiResult.class
        );
    }
}
