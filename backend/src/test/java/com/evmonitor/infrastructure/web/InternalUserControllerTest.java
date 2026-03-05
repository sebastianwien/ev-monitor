package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for InternalUserController.
 *
 * Verifies:
 * - Beta mode (PREMIUM_ENABLED=false, the test default): always returns {premium: true}
 * - Auth: missing/wrong X-Internal-Token → 403
 */
class InternalUserControllerTest extends AbstractIntegrationTest {

    // Default value from application.yml: ${INTERNAL_SERVICE_TOKEN:dev-internal-token-change-in-prod}
    private static final String VALID_INTERNAL_TOKEN = "dev-internal-token-change-in-prod";

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("internal-test-" + System.nanoTime() + "@example.com");
    }

    private HttpHeaders internalAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        return headers;
    }

    @Test
    void hasPremium_inBetaMode_alwaysReturnsTrue_regardlessOfUserPremiumStatus() {
        // PREMIUM_ENABLED=false (test default) → Wallbox free for all
        HttpEntity<Void> request = new HttpEntity<>(internalAuthHeaders(VALID_INTERNAL_TOKEN));

        ResponseEntity<Map<String, Boolean>> response = restTemplate.exchange(
                "/api/internal/users/" + testUser.getId() + "/has-premium",
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("premium"),
                "In beta mode, has-premium should always return true");
    }

    @Test
    void hasPremium_inBetaMode_returnsTrue_evenForUnknownUser() {
        // Beta mode short-circuits before DB lookup — so even a random UUID returns true
        UUID unknownId = UUID.randomUUID();
        HttpEntity<Void> request = new HttpEntity<>(internalAuthHeaders(VALID_INTERNAL_TOKEN));

        ResponseEntity<Map<String, Boolean>> response = restTemplate.exchange(
                "/api/internal/users/" + unknownId + "/has-premium",
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("premium"));
    }

    @Test
    void hasPremium_withoutInternalToken_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/internal/users/" + testUser.getId() + "/has-premium",
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void hasPremium_withWrongInternalToken_returns403() {
        HttpEntity<Void> request = new HttpEntity<>(internalAuthHeaders("wrong-token"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/users/" + testUser.getId() + "/has-premium",
                HttpMethod.GET,
                request,
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
