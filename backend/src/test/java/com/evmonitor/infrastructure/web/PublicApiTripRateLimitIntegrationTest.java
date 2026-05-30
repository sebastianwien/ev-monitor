package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiKeyCreatedResponse;
import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that POST /api/v1/trips enforces the 60 req/h rate limit per API key.
 * Rate limiting is disabled by default in tests - this class enables it explicitly.
 */
@TestPropertySource(properties = "app.security.rate-limiting.enabled=true")
class PublicApiTripRateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private Car car;
    private String plaintextKey;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("rate-limit-" + System.nanoTime() + "@ev-monitor.net");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Rate Limit Test Key");
        plaintextKey = created.plaintextKey();
    }

    @Test
    void createTrip_exceeding60RequestsPerHour_returns429() {
        Map<String, Object> body = Map.of(
                "car_id", car.getId().toString(),
                "started_at", "2025-06-01T08:00:00Z",
                "ended_at", "2025-06-01T09:00:00Z",
                "distance_km", 10.0
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + plaintextKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // Exhaust the 60-request bucket
        for (int i = 0; i < 60; i++) {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/trips", HttpMethod.POST, request, Map.class);
            assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                    "Request " + (i + 1) + " should succeed");
        }

        // 61st request must be rejected
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/trips", HttpMethod.POST, request, Map.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }
}
