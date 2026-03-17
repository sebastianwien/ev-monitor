package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TronityImportController.
 *
 * Covers: auth, ownership, JSON parsing, DataSource tagging, coin awards.
 */
class TronityImportControllerIntegrationTest extends AbstractIntegrationTest {

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("tronity-" + System.nanoTime() + "@ev-monitor.net");
        car  = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    // ── Auth & Ownership ──────────────────────────────────────────────────────

    @Test
    void unauthenticated_returns403() {
        Map<String, Object> body = Map.of("carId", car.getId(), "data", "[]");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/import/tronity", body, String.class);
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
    }

    @Test
    void otherUsersCar_returns403() {
        User other = createAndSaveUser("other-tronity-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);

        String json = """
                [{"date":"2026-03-15 20:10","kwh":50.69}]
                """;

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/tronity",
                HttpMethod.POST,
                createAuthRequest(Map.of("carId", otherCar.getId(), "data", json), user.getId(), user.getEmail()),
                Map.class
        );
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── Request validation ────────────────────────────────────────────────────

    @Test
    void missingCarId_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/tronity",
                HttpMethod.POST,
                createAuthRequest(Map.of("data", "[{\"date\":\"2026-03-15 20:10\",\"kwh\":10.0}]"), user.getId(), user.getEmail()),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void emptyData_returns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/import/tronity",
                HttpMethod.POST,
                createAuthRequest(Map.of("carId", car.getId(), "data", ""), user.getId(), user.getEmail()),
                Map.class
        );
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @Test
    void validJson_importsAndTagsAsTronity() {
        String json = """
                [
                  {"date":"2026-03-15 20:10","kwh":50.69,"odometer_km":114155,"soc_before":8,"soc_after":100,
                   "cost_eur":15.11,"duration_min":840,"location":"47.906604,11.831574","charging_type":"AC","max_charging_power_kw":4},
                  {"date":"2026-03-15 14:34","kwh":38.02,"odometer_km":113928,"soc_before":24,"soc_after":93,
                   "cost_eur":15.41,"duration_min":54,"location":"49.532374,12.13492","charging_type":"DC","max_charging_power_kw":54}
                ]
                """;

        ResponseEntity<ImportApiResult> response = post(json);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().imported());
        assertEquals(0, response.getBody().skipped());
        assertEquals(0, response.getBody().errors());

        List<EvLog> logs = evLogRepository.findAllByCarId(car.getId());
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(l -> l.getDataSource() == DataSource.TRONITY_IMPORT));
        assertTrue(logs.stream().allMatch(EvLog::isIncludeInStatistics));
    }

    @Test
    void validJson_geohashSetFromCoordinates() {
        String json = """
                [{"date":"2026-03-15 20:10","kwh":50.69,"location":"47.906604,11.831574"}]
                """;

        post(json);

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertNotNull(log.getGeohash());
        assertEquals(5, log.getGeohash().length());
    }

    @Test
    void duplicateEntry_isSkipped() {
        String json = """
                [
                  {"date":"2026-03-15 20:10","kwh":50.69},
                  {"date":"2026-03-15 20:10","kwh":50.69}
                ]
                """;

        ResponseEntity<ImportApiResult> response = post(json);

        assertEquals(2, response.getBody().imported() + response.getBody().skipped());
        assertEquals(1, response.getBody().skipped());
    }

    @Test
    void validJson_rawImportDataStored() {
        String json = """
                [{"date":"2026-03-15 20:10","kwh":50.69,"raw_import_data":"{\\"Start Datum\\":\\"15.03.2026 20:10\\",\\"Geladen (kWh)\\": 50.69}"}]
                """;

        post(json);

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertNotNull(log.getRawImportData());
        assertTrue(log.getRawImportData().contains("Start Datum"));
    }

    @Test
    void validJson_coinsAwarded() {
        String json = """
                [{"date":"2026-03-15 20:10","kwh":50.69}]
                """;

        post(json);

        List<CoinLog> coins = coinLogRepository.findAllByUserId(user.getId());
        assertEquals(1, coins.stream().filter(c -> "Ladevorgang importiert (API)".equals(c.getActionDescription())).count());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<ImportApiResult> post(String data) {
        Map<String, Object> body = Map.of("carId", car.getId(), "data", data);
        return restTemplate.exchange(
                "/api/import/tronity",
                HttpMethod.POST,
                createAuthRequest(body, user.getId(), user.getEmail()),
                ImportApiResult.class
        );
    }
}
