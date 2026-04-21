package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.SmartcarWebhookRawLogRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for InternalSmartcarWebhookController.
 *
 * Verifies:
 * - POST /api/internal/smartcar/webhook-log: persist raw webhook event
 * - Auth: missing/wrong X-Internal-Token -> 403
 * - Idempotency: duplicate eventId -> 200 (no crash)
 */
class InternalSmartcarWebhookControllerTest extends AbstractIntegrationTest {

    private static final String VALID_TOKEN = "test-internal-token";

    @Autowired
    SmartcarWebhookRawLogRepository repo;

    @Test
    void logWebhook_withValidToken_returns200AndPersists() {
        Map<String, Object> request = webhookLogRequest("event-abc-001", "vehicle-xyz");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/internal/smartcar/webhook-log", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(repo.existsByEventId("event-abc-001"));
    }

    @Test
    void logWebhook_withoutToken_returns403() {
        Map<String, Object> request = webhookLogRequest("event-abc-002", "vehicle-xyz");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/smartcar/webhook-log", HttpMethod.POST,
                new HttpEntity<>(request), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void logWebhook_withWrongToken_returns403() {
        Map<String, Object> request = webhookLogRequest("event-abc-003", "vehicle-xyz");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/smartcar/webhook-log", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders("wrong-token")), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void logWebhook_duplicateEventId_returns200Idempotent() {
        Map<String, Object> request = webhookLogRequest("event-abc-dup", "vehicle-xyz");

        restTemplate.exchange("/api/internal/smartcar/webhook-log", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Void.class);

        ResponseEntity<Void> second = restTemplate.exchange(
                "/api/internal/smartcar/webhook-log", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Void.class);

        assertEquals(HttpStatus.OK, second.getStatusCode());
    }

    @Test
    void logWebhook_persistsSignalFields() {
        Map<String, Object> request = webhookLogRequest("event-abc-fields", "vehicle-sig");
        request.put("socPercent", 78);
        request.put("odometerKm", "78432.0");
        request.put("locationGeohash", "u2ey3d7q");
        request.put("outsideTempCelsius", "14.5");
        request.put("mode", "LIVE");

        restTemplate.exchange("/api/internal/smartcar/webhook-log", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Void.class);

        var saved = repo.findByEventId("event-abc-fields");
        assertTrue(saved.isPresent());
        assertEquals("vehicle-sig", saved.get().getSmartcarVehicleId());
        assertEquals(0, new BigDecimal("78").compareTo(saved.get().getSocPercent()));
        assertEquals("u2ey3d7q", saved.get().getLocationGeohash());
        assertEquals("LIVE", saved.get().getMode());
    }

    // --- helpers ---

    private HttpHeaders internalHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> webhookLogRequest(String eventId, String vehicleId) {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("smartcarVehicleId", vehicleId);
        map.put("make", "Tesla");
        map.put("model", "Model 3");
        map.put("year", 2020);
        map.put("triggersJson", "[{\"type\":\"SIGNAL_UPDATED\",\"signal\":{\"code\":\"odometer-traveleddistance\"}}]");
        map.put("signalsJson", "[{\"code\":\"odometer-traveleddistance\",\"body\":{\"value\":78432}}]");
        return map;
    }
}
