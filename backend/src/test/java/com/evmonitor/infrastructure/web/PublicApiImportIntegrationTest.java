package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.application.publicapi.ApiKeyCreatedResponse;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for POST /api/v1/sessions (Public Upload API).
 *
 * Covers: Tier-1 minimal upload, Tier-2 full upload, deduplication,
 * auth (invalid key → 401), ownership check (foreign car → 403), batch limit.
 */
class PublicApiImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private User user;
    private Car car;
    private String plaintextKey;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("api-upload-" + System.nanoTime() + "@ev-monitor.net");
        car  = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Test Key");
        plaintextKey = created.plaintextKey();
    }

    // ── Tier-1 minimal upload ─────────────────────────────────────────────────

    @Test
    void tier1_minimalUpload_importsSession() {
        String body = """
                {
                  "car_id": "%s",
                  "sessions": [
                    { "date": "2024-11-15T14:30:00", "kwh": 32.5 }
                  ]
                }
                """.formatted(car.getId());

        ResponseEntity<Map> response = post(body, plaintextKey);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().get("imported"));
        assertEquals(0, response.getBody().get("skipped"));

        List<EvLog> logs = evLogRepository.findAllByCarId(car.getId());
        assertEquals(1, logs.size());
        EvLog log = logs.getFirst();
        assertEquals(DataSource.API_UPLOAD, log.getDataSource());
        assertTrue(log.isIncludeInStatistics());
        assertNull(log.getOdometerKm());
        assertNull(log.getSocAfterChargePercent());
    }

    // ── Tier-2 full upload ────────────────────────────────────────────────────

    @Test
    void tier2_fullUpload_storesAllFields() {
        String body = """
                {
                  "car_id": "%s",
                  "sessions": [
                    {
                      "date": "2024-11-15T14:30:00",
                      "kwh": 32.5,
                      "odometer_km": 45230,
                      "soc_before": 12,
                      "soc_after": 85,
                      "cost_eur": 9.10,
                      "duration_min": 47,
                      "location": "48.1234,11.5678",
                      "charging_type": "AC",
                      "max_charging_power_kw": 11.0,
                      "route_type": "COMBINED",
                      "tire_type": "SUMMER"
                    }
                  ]
                }
                """.formatted(car.getId());

        ResponseEntity<Map> response = post(body, plaintextKey);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("imported"));

        EvLog log = evLogRepository.findAllByCarId(car.getId()).getFirst();
        assertEquals(45230, log.getOdometerKm());
        assertEquals(85, log.getSocAfterChargePercent());
        assertEquals(12, log.getSocBeforeChargePercent());
        assertEquals(ChargingType.AC, log.getChargingType());
        assertEquals(RouteType.COMBINED, log.getRouteType());
        assertEquals(TireType.SUMMER, log.getTireType());
        assertNotNull(log.getGeohash()); // location was parsed to geohash
        assertNotNull(log.getCostEur());
        assertEquals(47, log.getChargeDurationMinutes());
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    @Test
    void deduplication_withOdometer_skipsExisting() {
        String session = """
                { "date": "2024-11-15T14:30:00", "kwh": 32.5, "odometer_km": 45230 }
                """;
        String body = """
                { "car_id": "%s", "sessions": [%s] }
                """.formatted(car.getId(), session);

        post(body, plaintextKey);
        ResponseEntity<Map> second = post(body, plaintextKey);

        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(0, second.getBody().get("imported"));
        assertEquals(1, second.getBody().get("skipped"));

        assertEquals(1, evLogRepository.findAllByCarId(car.getId()).size());
    }

    @Test
    void deduplication_withoutOdometer_skipsWithinTimeWindow() {
        String session = """
                { "date": "2024-11-15T14:30:00", "kwh": 32.5 }
                """;
        String body = """
                { "car_id": "%s", "sessions": [%s] }
                """.formatted(car.getId(), session);

        post(body, plaintextKey);
        ResponseEntity<Map> second = post(body, plaintextKey);

        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(0, second.getBody().get("imported"));
        assertEquals(1, second.getBody().get("skipped"));
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Test
    void invalidKey_returns401() {
        String body = """
                { "car_id": "%s", "sessions": [{ "date": "2024-11-15T14:30:00", "kwh": 10.0 }] }
                """.formatted(car.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer evm_thisIsNotAValidKey");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void noAuthHeader_returns401() {
        String body = """
                { "car_id": "%s", "sessions": [{ "date": "2024-11-15T14:30:00", "kwh": 10.0 }] }
                """.formatted(car.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/sessions", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── Ownership check ───────────────────────────────────────────────────────

    @Test
    void foreignCar_returns403() {
        // Create a second user with their own car
        User otherUser = createAndSaveUser("other-" + System.nanoTime() + "@ev-monitor.net");
        Car otherCar = createAndSaveCar(otherUser.getId(), CarBrand.CarModel.MODEL_3);

        String body = """
                {
                  "car_id": "%s",
                  "sessions": [{ "date": "2024-11-15T14:30:00", "kwh": 10.0 }]
                }
                """.formatted(otherCar.getId());

        ResponseEntity<Map> response = post(body, plaintextKey);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── Batch limit ───────────────────────────────────────────────────────────

    @Test
    void moreThan100Sessions_returns400() {
        StringBuilder sessions = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) sessions.append(",");
            sessions.append("""
                    { "date": "2024-11-15T%02d:00:00", "kwh": 10.0 }
                    """.formatted(i % 24));
        }
        String body = """
                { "car_id": "%s", "sessions": [%s] }
                """.formatted(car.getId(), sessions);

        ResponseEntity<Map> response = post(body, plaintextKey);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── Coins ─────────────────────────────────────────────────────────────────

    @Test
    void upload_awardsCoinsPerSession() {
        String body = """
                {
                  "car_id": "%s",
                  "sessions": [
                    { "date": "2024-11-10T10:00:00", "kwh": 20.0 },
                    { "date": "2024-11-11T10:00:00", "kwh": 25.0 }
                  ]
                }
                """.formatted(car.getId());

        ResponseEntity<Map> response = post(body, plaintextKey);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().get("imported"));

        int totalCoins = coinLogRepository.findAllByUserId(user.getId())
                .stream().mapToInt(c -> c.getAmount()).sum();
        assertEquals(4, totalCoins); // 2 coins per session × 2 sessions
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map> post(String jsonBody, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/v1/sessions", HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), Map.class);
    }
}
