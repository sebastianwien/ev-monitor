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

    // Default value from application.yml: ${INTERNAL_SERVICE_TOKEN:test-internal-token}
    private static final String VALID_INTERNAL_TOKEN = "test-internal-token";

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

    @Test
    void entitlementsBatch_returnsEntitlementForEachKnownUser() {
        User second = createAndSaveUser("internal-test-2-" + System.nanoTime() + "@example.com");
        HttpHeaders headers = internalAuthHeaders(VALID_INTERNAL_TOKEN);
        Map<String, Object> body = Map.of("userIds", java.util.List.of(
                testUser.getId().toString(), second.getId().toString()));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
                "/api/internal/users/entitlements",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Map<String, Object>> result = response.getBody();
        assertNotNull(result);
        assertTrue(result.containsKey(testUser.getId().toString()));
        assertTrue(result.containsKey(second.getId().toString()));
        Map<String, Object> first = result.get(testUser.getId().toString());
        assertNotNull(first.get("canActivate"));
        assertNotNull(first.get("role"));
        assertNotNull(first.get("premium"));
    }

    @Test
    void entitlementsBatch_unknownUserId_omittedFromResponse() {
        UUID unknown = UUID.randomUUID();
        HttpHeaders headers = internalAuthHeaders(VALID_INTERNAL_TOKEN);
        Map<String, Object> body = Map.of("userIds", java.util.List.of(
                testUser.getId().toString(), unknown.toString()));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
                "/api/internal/users/entitlements",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Map<String, Object>> result = response.getBody();
        assertNotNull(result);
        assertTrue(result.containsKey(testUser.getId().toString()));
        assertFalse(result.containsKey(unknown.toString()),
                "Unknown user ids should be omitted, not returned with null values");
    }

    @Test
    void entitlementsBatch_withoutInternalToken_returns403() {
        Map<String, Object> body = Map.of("userIds", java.util.List.of(testUser.getId().toString()));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/internal/users/entitlements",
                HttpMethod.POST,
                request,
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void entitlementsBatch_emptyList_returnsEmptyMap() {
        HttpHeaders headers = internalAuthHeaders(VALID_INTERNAL_TOKEN);
        Map<String, Object> body = Map.of("userIds", java.util.List.of());
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
                "/api/internal/users/entitlements",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    // --- source-aware telemetry-access: Tesla activation is free ---

    @Test
    void telemetryAccess_sourceTesla_freeUser_canActivateWithFullProfile() {
        // testUser is a free (NONE-tier) user: Tesla activation must be free, FULL profile.
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/internal/users/" + testUser.getId() + "/telemetry-access?source=TESLA",
                HttpMethod.GET,
                new HttpEntity<>(internalAuthHeaders(VALID_INTERNAL_TOKEN)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(true, res.getBody().get("canActivate"));
        assertEquals("FULL", res.getBody().get("preferredProfile"));
    }

    @Test
    void telemetryAccess_sourceSmartcar_freeUser_cannotActivate() {
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/internal/users/" + testUser.getId() + "/telemetry-access?source=SMARTCAR",
                HttpMethod.GET,
                new HttpEntity<>(internalAuthHeaders(VALID_INTERNAL_TOKEN)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(false, res.getBody().get("canActivate"));
    }

    @Test
    void telemetryAccess_noSource_defaultsToPaid_failClosed() {
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                "/api/internal/users/" + testUser.getId() + "/telemetry-access",
                HttpMethod.GET,
                new HttpEntity<>(internalAuthHeaders(VALID_INTERNAL_TOKEN)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(false, res.getBody().get("canActivate"));
    }
}
