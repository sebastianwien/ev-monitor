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
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + plaintextKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Exhaust the 60-request bucket. Each trip gets its own start time,
        // because an identical started_at is rejected as a duplicate (400).
        for (int i = 0; i < 60; i++) {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "/api/v1/trips", HttpMethod.POST, tripRequest(i, headers), Map.class);
            assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                    "Request " + (i + 1) + " should succeed");
        }

        // 61st request must be rejected
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/trips", HttpMethod.POST, tripRequest(60, headers), Map.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }

    private HttpEntity<Map<String, Object>> tripRequest(int dayOffset, HttpHeaders headers) {
        Map<String, Object> body = Map.of(
                "car_id", car.getId().toString(),
                "started_at", java.time.OffsetDateTime.parse("2025-01-01T08:00:00Z").plusDays(dayOffset).toString(),
                "ended_at", java.time.OffsetDateTime.parse("2025-01-01T09:00:00Z").plusDays(dayOffset).toString(),
                "distance_km", 10.0
        );
        return new HttpEntity<>(body, headers);
    }
}
