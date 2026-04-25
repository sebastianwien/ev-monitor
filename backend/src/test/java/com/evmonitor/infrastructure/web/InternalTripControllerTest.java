package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for POST /api/internal/trips.
 *
 * Verifies:
 * - Happy path: trip persisted, id returned
 * - Idempotency: same externalId -> 200, no duplicate
 * - Auth: missing/wrong token -> 403
 */
class InternalTripControllerTest extends AbstractIntegrationTest {

    private static final String VALID_TOKEN = "test-internal-token";

    @Autowired
    EvTripRepository tripRepository;

    @Test
    void submitTrip_withValidToken_returns200AndPersists() {
        UUID externalId = UUID.randomUUID();
        UUID carId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/trips", HttpMethod.POST,
                new HttpEntity<>(tripRequest(externalId, carId, userId), internalHeaders(VALID_TOKEN)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("id"));

        var saved = tripRepository.findByExternalIdAndDeletedAtIsNull(externalId);
        assertTrue(saved.isPresent());
        assertEquals("TESLA_LIVE", saved.get().getDataSource());
        assertEquals(carId, saved.get().getCarId());
        assertEquals(0, new BigDecimal("82").compareTo(saved.get().getSocStart()));
        assertEquals(0, new BigDecimal("71").compareTo(saved.get().getSocEnd()));
        assertEquals("COMPLETED", saved.get().getStatus());
    }

    @Test
    void submitTrip_idempotent_sameExternalIdReturns200WithoutDuplicate() {
        UUID externalId = UUID.randomUUID();
        UUID carId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var request = new HttpEntity<>(tripRequest(externalId, carId, userId), internalHeaders(VALID_TOKEN));

        restTemplate.exchange("/api/internal/trips", HttpMethod.POST, request, Map.class);
        ResponseEntity<Map> second = restTemplate.exchange("/api/internal/trips", HttpMethod.POST, request, Map.class);

        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(1, tripRepository.findAll().stream()
                .filter(t -> externalId.equals(t.getExternalId())).count());
    }

    @Test
    void submitTrip_withoutToken_returns403() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/trips", HttpMethod.POST,
                new HttpEntity<>(tripRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void submitTrip_withWrongToken_returns403() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/trips", HttpMethod.POST,
                new HttpEntity<>(tripRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                        internalHeaders("wrong-token")),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void submitTrip_withoutUserId_returns400() {
        Map<String, Object> request = tripRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        request.remove("userId");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/trips", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void submitTrip_withoutExternalId_persistsWithNullExternalId() {
        Map<String, Object> request = tripRequest(null, UUID.randomUUID(), UUID.randomUUID());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/trips", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UUID id = UUID.fromString(response.getBody().get("id").toString());
        EvTrip saved = tripRepository.findById(id).orElseThrow();
        assertNull(saved.getExternalId());
    }

    // --- helpers ---

    private HttpHeaders internalHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> tripRequest(UUID externalId, UUID carId, UUID userId) {
        Map<String, Object> map = new HashMap<>();
        if (externalId != null) map.put("externalId", externalId.toString());
        map.put("carId", carId.toString());
        map.put("userId", userId.toString());
        map.put("dataSource", "TESLA_LIVE");
        map.put("tripStartedAt", "2026-04-20T16:07:35Z");
        map.put("tripEndedAt", "2026-04-20T16:18:42Z");
        map.put("socStart", 82);
        map.put("socEnd", 71);
        map.put("odometerStartKm", "62180.9");
        map.put("odometerEndKm", "62195.3");
        map.put("distanceKm", "14.4");
        map.put("locationStartGeohash", "u2ewmk");
        map.put("locationEndGeohash", "u2ewpz");
        map.put("outsideTempCelsius", "14.5");
        map.put("nominalFullPackKwh", "82.0");
        map.put("estimatedConsumedKwh", "9.02");
        map.put("status", "COMPLETED");
        return map;
    }
}
