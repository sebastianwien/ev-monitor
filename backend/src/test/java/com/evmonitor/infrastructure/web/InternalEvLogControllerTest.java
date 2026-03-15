package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for InternalEvLogController.
 *
 * Verifies:
 * - POST /api/internal/logs: create log from internal service (TESLA_FLEET, WALLBOX_GOE etc.)
 * - PATCH /api/internal/logs/geohash: update geohash of an existing log
 * - Auth: missing/wrong X-Internal-Token → 403
 */
class InternalEvLogControllerTest extends AbstractIntegrationTest {

    private static final String VALID_TOKEN = "dev-internal-token-change-in-prod";

    private User testUser;
    private Car testCar;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("internal-evlog-" + System.nanoTime() + "@example.com");
        testCar = createAndSaveCar(testUser.getId(), CarBrand.CarModel.MODEL_3);
    }

    // --- POST /api/internal/logs ---

    @Test
    void createLog_withValidInternalToken_returnsCreatedLog() {
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "45.5", 60, LocalDateTime.now().minusHours(1), null, "TESLA_FLEET_IMPORT", "14.50");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testCar.getId().toString(), response.getBody().get("carId").toString());
    }

    @Test
    void createLog_withCostAndGeohash_persistsBothFields() {
        LocalDateTime loggedAt = LocalDateTime.now().minusHours(2);
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "50.0", 90, loggedAt, "u2edq", "TESLA_FLEET_IMPORT", "18.00");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals("u2edq", body.get("geohash"));
        assertEquals("18.0", body.get("costEur").toString());
    }

    @Test
    void createLog_withoutCostOrGeohash_createsLogWithNulls() {
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "30.0", 45, LocalDateTime.now().minusHours(3), null, "WALLBOX_GOE", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().get("geohash"));
        assertNull(response.getBody().get("costEur"));
    }

    @Test
    void createLog_withoutInternalToken_returns403() {
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "45.0", 60, LocalDateTime.now(), null, "TESLA_FLEET_IMPORT", null);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createLog_withWrongInternalToken_returns403() {
        Map<String, Object> request = logRequest(testCar.getId(), testUser.getId(),
                "45.0", 60, LocalDateTime.now(), null, "TESLA_FLEET_IMPORT", null);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders("wrong-token")), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // --- PATCH /api/internal/logs/geohash ---

    @Test
    void updateGeohash_updatesGeohashOnExistingLog() {
        // Create a log first (without geohash)
        LocalDateTime loggedAt = LocalDateTime.now().minusHours(5).withNano(0).withSecond(0);
        Map<String, Object> createRequest = logRequest(testCar.getId(), testUser.getId(),
                "60.0", 75, loggedAt, null, "TESLA_FLEET_IMPORT", "22.50");
        restTemplate.exchange("/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(createRequest, internalHeaders(VALID_TOKEN)), Map.class);

        // Patch the geohash
        Map<String, Object> patchRequest = Map.of(
                "carId", testCar.getId().toString(),
                "userId", testUser.getId().toString(),
                "loggedAt", loggedAt.toString(),
                "geohash", "u2edq"
        );

        ResponseEntity<Void> patchResponse = restTemplate.exchange(
                "/api/internal/logs/geohash", HttpMethod.PATCH,
                new HttpEntity<>(patchRequest, internalHeaders(VALID_TOKEN)), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, patchResponse.getStatusCode());

        // Verify via user-authenticated GET
        String userToken = createAuthHeaders(testUser.getId(), testUser.getEmail())
                .getFirst("Authorization").substring(7);
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.set("Authorization", "Bearer " + userToken);
        ResponseEntity<Map[]> logsResponse = restTemplate.exchange(
                "/api/logs", HttpMethod.GET,
                new HttpEntity<>(userHeaders), Map[].class);

        assertEquals(HttpStatus.OK, logsResponse.getStatusCode());
        Map[] logs = logsResponse.getBody();
        assertNotNull(logs);
        boolean geohashFound = java.util.Arrays.stream(logs)
                .anyMatch(log -> "u2edq".equals(log.get("geohash")));
        assertTrue(geohashFound, "Geohash should be updated to u2edq");
    }

    @Test
    void updateGeohash_withoutInternalToken_returns403() {
        Map<String, Object> request = Map.of(
                "carId", testCar.getId().toString(),
                "userId", testUser.getId().toString(),
                "loggedAt", LocalDateTime.now().toString(),
                "geohash", "u2edq"
        );

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/logs/geohash", HttpMethod.PATCH,
                new HttpEntity<>(request), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // --- Helpers ---

    private HttpHeaders internalHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> logRequest(UUID carId, UUID userId, String kwh,
                                            int durationMinutes, LocalDateTime loggedAt,
                                            String geohash, String dataSource, String costEur) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("carId", carId.toString());
        map.put("userId", userId.toString());
        map.put("kwhCharged", kwh);
        map.put("chargeDurationMinutes", durationMinutes);
        map.put("loggedAt", loggedAt.toString());
        map.put("geohash", geohash);
        map.put("dataSource", dataSource);
        map.put("costEur", costEur);
        return map;
    }
}
