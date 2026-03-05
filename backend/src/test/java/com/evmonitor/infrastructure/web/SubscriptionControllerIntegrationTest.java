package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for SubscriptionController.
 *
 * Tests run with PREMIUM_ENABLED=false (beta mode, the default for tests).
 * This covers the most critical paths: auth guards and beta-mode behavior.
 */
class SubscriptionControllerIntegrationTest extends AbstractIntegrationTest {

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("subscription-test-" + System.nanoTime() + "@example.com");
    }

    // --- GET /api/subscription/status ---

    @Test
    void getStatus_whenAuthenticated_returnsIsPremiumAndPremiumEnabled() {
        HttpEntity<Void> request = createAuthRequest(testUser.getId(), testUser.getEmail());

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/subscription/status",
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().get("isPremium"));
        assertEquals(false, response.getBody().get("premiumEnabled"));
    }

    @Test
    void getStatus_withoutAuth_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/subscription/status", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.FORBIDDEN ||
                response.getStatusCode() == HttpStatus.UNAUTHORIZED,
                "Expected 403 or 401, got: " + response.getStatusCode());
    }

    // --- POST /api/subscription/checkout ---

    @Test
    void createCheckout_whenPremiumDisabled_returns503() {
        Map<String, String> body = Map.of("plan", "monthly");
        HttpEntity<Map<String, String>> request = createAuthRequest(
                body, testUser.getId(), testUser.getEmail());

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/subscription/checkout",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("message"));
    }

    @Test
    void createCheckout_withoutAuth_returns403() {
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("plan", "monthly"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/subscription/checkout",
                HttpMethod.POST,
                request,
                String.class
        );

        assertTrue(
                response.getStatusCode() == HttpStatus.FORBIDDEN ||
                response.getStatusCode() == HttpStatus.UNAUTHORIZED,
                "Expected 403 or 401, got: " + response.getStatusCode());
    }
}
