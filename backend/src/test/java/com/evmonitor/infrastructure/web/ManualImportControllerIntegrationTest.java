package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ManualImportController.
 *
 * Covers: auth, ownership, CSV/JSON parsing, invalid inputs, coin awards,
 * location handling, and session grouping (merge).
 */
class ManualImportControllerIntegrationTest extends AbstractIntegrationTest {

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("manualimport-" + System.nanoTime() + "@ev-monitor.net");
        car  = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    // ── Auth & Ownership ──────────────────────────────────────────────────────

    @Test
    void unauthenticated_returns403() {
        Map<String, Object> body = Map.of("carId", car.getId(), "format", "csv", "data", "x");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/import/sessions", body, String.class);
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
    }

    @Test
    void otherUsersCar_returns403() {
        User other = createAndSaveUser("other-manualimport-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);

        String csv = "date,kwh\n2025-08-20T10:00:00,24.5\n";

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/sessions",
                HttpMethod.POST,
                createAuthRequest(
                        Map.of("carId", otherCar.getId(), "format", "csv", "data", csv),
                        user.getId(), user.getEmail()
                ),
                Map.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── Request validation ────────────────────────────────────────────────────

    @Test
    void missingCarId_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/sessions",
                HttpMethod.POST,
                createAuthRequest(
                        Map.of("format", "csv", "data", "date,kwh\n2025-01-01,10.0\n"),
                        user.getId(), user.getEmail()
                ),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void emptyData_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/sessions",
                HttpMethod.POST,
                createAuthRequest(
                        Map.of("carId", car.getId(), "format", "csv", "data", ""),
                        user.getId(), user.getEmail()
                ),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void invalidFormat_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/sessions",
                HttpMethod.POST,
                createAuthRequest(
                        Map.of("carId", car.getId(), "format", "xml", "data", "<root/>"),
                        user.getId(), user.getEmail()
                ),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── CSV import ────────────────────────────────────────────────────────────

    @Test
    void csvImport_validRows_importedAndCoinsAwarded() {
        String csv = """
                date,kwh,odometer_km,soc_before,soc_after,cost_eur,duration_min
                2025-08-20T10:56:48,24.5,12345,45,80,8.50,35
                2025-09-01T14:22:00,18.2,13102,30,72,,28
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().imported());
        assertEquals(0, response.getBody().skipped());
        assertEquals(0, response.getBody().errors());

        // Coins: 2 per imported log
        List<CoinLog> coins = coinLogRepository.findAllByUserId(user.getId());
        assertEquals(2, coins.stream().filter(c -> "Ladevorgang importiert (API)".equals(c.getActionDescription())).count());
    }

    @Test
    void csvImport_missingDate_countedAsError() {
        String csv = """
                date,kwh
                ,24.5
                2025-09-01T14:22:00,18.2
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
    }

    @Test
    void csvImport_missingKwh_countedAsError() {
        String csv = """
                date,kwh
                2025-08-20T10:00:00,
                2025-09-01T10:00:00,18.2
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().imported());
        assertEquals(1, response.getBody().errors());
    }

    @Test
    void csvImport_allRowsInvalid_returnsAllErrors() {
        String csv = """
                date,kwh
                ,24.5
                ,18.2
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().imported());
        assertEquals(2, response.getBody().errors());
    }

    @Test
    void csvImport_latLon_computesGeohash() {
        String csv = """
                date,kwh,location
                2025-08-20T10:00:00,20.0,48.2082 16.3738
                """;

        post(csv, "csv");

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertNotNull(log.getGeohash());
        assertEquals(5, log.getGeohash().length());
    }

    @Test
    void csvImport_placeName_geohashIsNull() {
        String csv = """
                date,kwh,location
                2025-08-20T10:00:00,20.0,IONITY Frankfurt
                """;

        post(csv, "csv");

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertNull(log.getGeohash());
    }

    // ── Duplicate detection ───────────────────────────────────────────────────

    @Test
    void csvImport_sameTimestampAndKwh_isSkipped() {
        String csv = """
                date,kwh
                2025-08-20T10:00:00,24.5
                2025-08-20T10:00:00,24.5
                2025-09-01T10:00:00,18.2
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().imported());
        assertEquals(1, response.getBody().skipped());
    }

    @Test
    void csvImport_sameTimestampDifferentKwh_notDuplicate() {
        String csv = """
                date,kwh
                2025-08-20T10:00:00,24.5
                2025-08-20T10:00:00,18.2
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv");

        assertEquals(2, response.getBody().imported());
        assertEquals(0, response.getBody().skipped());
    }

    // ── Session merging ───────────────────────────────────────────────────────

    @Test
    void csvImport_mergeSessions_groupsAdjacentSessionsOnSameDay() {
        // Rows intentionally out of order to also verify chronological sort before merge
        // Layout: S1 (Aug 19), S2+S3+S4 (Aug 20, gaps <90 min), S5 (Aug 21)
        String csv = """
                date,kwh,duration_min
                2025-08-20T09:45:00,7.0,20
                2025-08-19T10:00:00,10.0,30
                2025-08-21T15:00:00,20.0,60
                2025-08-20T08:00:00,5.0,30
                2025-08-20T09:00:00,6.0,30
                """;

        ResponseEntity<ImportApiResult> response = post(csv, "csv", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().imported());

        List<EvLog> sorted = evLogRepository.findAllByCarId(car.getId()).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        // S2, S3, S4 (indices 1-3) are on Aug 20 with gaps <90 min — must share a group
        UUID mergedGroup = sorted.get(1).getSessionGroupId();
        assertNotNull(mergedGroup);
        assertEquals(mergedGroup, sorted.get(2).getSessionGroupId());
        assertEquals(mergedGroup, sorted.get(3).getSessionGroupId());

        // S1 (Aug 19) and S5 (Aug 21) are on different days — own groups
        assertNotEquals(mergedGroup, sorted.get(0).getSessionGroupId());
        assertNotEquals(mergedGroup, sorted.get(4).getSessionGroupId());
    }

    @Test
    void jsonImport_mergeSessions_groupsAdjacentSessionsOnSameDay() {
        String json = """
                [
                  {"date":"2025-08-19T10:00:00","kwh":10.0,"duration_min":30},
                  {"date":"2025-08-20T08:00:00","kwh":5.0,"duration_min":30},
                  {"date":"2025-08-20T09:00:00","kwh":6.0,"duration_min":30},
                  {"date":"2025-08-20T09:45:00","kwh":7.0,"duration_min":20},
                  {"date":"2025-08-21T15:00:00","kwh":20.0,"duration_min":60}
                ]
                """;

        ResponseEntity<ImportApiResult> response = post(json, "json", true);

        assertEquals(5, response.getBody().imported());

        List<EvLog> sorted = evLogRepository.findAllByCarId(car.getId()).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        UUID mergedGroup = sorted.get(1).getSessionGroupId();
        assertNotNull(mergedGroup);
        assertEquals(mergedGroup, sorted.get(2).getSessionGroupId());
        assertEquals(mergedGroup, sorted.get(3).getSessionGroupId());

        assertNotEquals(mergedGroup, sorted.get(0).getSessionGroupId());
        assertNotEquals(mergedGroup, sorted.get(4).getSessionGroupId());
    }

    // ── JSON import ───────────────────────────────────────────────────────────

    @Test
    void jsonImport_validArray_importedSuccessfully() {
        String json = """
                [
                  {"date":"2025-08-20T10:56:48","kwh":24.5,"odometer_km":12345,"soc_after":80},
                  {"date":"2025-09-01T14:22:00","kwh":18.2,"odometer_km":13102,"soc_after":72}
                ]
                """;

        ResponseEntity<ImportApiResult> response = post(json, "json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().imported());
        assertEquals(0, response.getBody().errors());
    }

    @Test
    void jsonImport_invalidJson_returns200WithError() {
        String json = "not valid json at all";

        ResponseEntity<ImportApiResult> response = post(json, "json");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().imported());
        assertTrue(response.getBody().errors() > 0);
    }

    @Test
    void jsonImport_rawImportDataIsNull() {
        String json = """
                [{"date":"2025-08-20T10:56:48","kwh":24.5}]
                """;

        post(json, "json");

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertNull(log.getRawImportData());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<ImportApiResult> post(String data, String format) {
        return post(data, format, false);
    }

    private ResponseEntity<ImportApiResult> post(String data, String format, boolean mergeSessions) {
        Map<String, Object> body = Map.of(
                "carId", car.getId(),
                "format", format,
                "data", data,
                "mergeSessions", mergeSessions
        );
        return restTemplate.exchange(
                "/api/import/sessions",
                HttpMethod.POST,
                createAuthRequest(body, user.getId(), user.getEmail()),
                ImportApiResult.class
        );
    }
}
