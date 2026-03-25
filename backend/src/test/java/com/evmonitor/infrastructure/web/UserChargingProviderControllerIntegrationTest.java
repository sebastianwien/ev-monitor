package com.evmonitor.infrastructure.web;

import com.evmonitor.application.user.UserChargingProviderRequest;
import com.evmonitor.application.user.UserChargingProviderResponse;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserChargingProviderController.
 *
 * SECURITY CRITICAL: Users must only access their own charging providers!
 * Tests cover CRUD flow, authentication enforcement, and ownership checks.
 */
class UserChargingProviderControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JpaUserChargingProviderRepository providerRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        providerRepository.deleteAll();
        userA = createAndSaveUser("provider-test-a-" + System.nanoTime() + "@example.com");
        userB = createAndSaveUser("provider-test-b-" + System.nanoTime() + "@example.com");
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    @Test
    void shouldReturn403_WhenUnauthenticated() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/users/me/charging-providers", String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void shouldReturn403_WhenUnauthenticated_OnPost() {
        UserChargingProviderRequest request = buildRequest("IONITY", LocalDate.now());
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/users/me/charging-providers", request, String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── GET /active ───────────────────────────────────────────────────────────

    @Test
    void shouldReturn204_WhenNoActiveProvider() {
        HttpEntity<Void> auth = createAuthRequest(userA.getId(), userA.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/users/me/charging-providers/active", HttpMethod.GET, auth, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    @Test
    void shouldAddProvider_AndReturnCorrectFields() {
        UserChargingProviderRequest request = buildRequest("IONITY", LocalDate.of(2026, 1, 1));
        HttpEntity<UserChargingProviderRequest> auth = createAuthRequest(request, userA.getId(), userA.getEmail());

        ResponseEntity<UserChargingProviderResponse> response = restTemplate.postForEntity(
                "/api/users/me/charging-providers", auth, UserChargingProviderResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserChargingProviderResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("IONITY", body.providerName());
        assertEquals(0, new BigDecimal("0.29").compareTo(body.acPricePerKwh()));
        assertEquals(0, new BigDecimal("0.49").compareTo(body.dcPricePerKwh()));
        assertEquals(LocalDate.of(2026, 1, 1), body.activeFrom());
        assertNull(body.activeUntil());
        assertNotNull(body.id());
    }

    // ── GET /active after POST ────────────────────────────────────────────────

    @Test
    void shouldGetActiveProvider_AfterAdding() {
        addProvider(userA, "EnBW", LocalDate.of(2026, 1, 1));

        HttpEntity<Void> auth = createAuthRequest(userA.getId(), userA.getEmail());
        ResponseEntity<UserChargingProviderResponse> response = restTemplate.exchange(
                "/api/users/me/charging-providers/active", HttpMethod.GET, auth,
                UserChargingProviderResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("EnBW", response.getBody().providerName());
    }

    // ── GET all ───────────────────────────────────────────────────────────────

    @Test
    void shouldGetAllProviders_ReturnsHistory() {
        addProvider(userA, "EnBW", LocalDate.of(2025, 1, 1));
        addProvider(userA, "IONITY", LocalDate.of(2026, 1, 1));

        HttpEntity<Void> auth = createAuthRequest(userA.getId(), userA.getEmail());
        ResponseEntity<List<UserChargingProviderResponse>> response = restTemplate.exchange(
                "/api/users/me/charging-providers", HttpMethod.GET, auth,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        // ordered by activeFrom DESC — IONITY first
        assertEquals("IONITY", response.getBody().get(0).providerName());
    }

    @Test
    void shouldOnlyReturnOwnProviders_NotOtherUsers() {
        addProvider(userA, "IONITY", LocalDate.of(2026, 1, 1));
        addProvider(userB, "Fastned", LocalDate.of(2026, 1, 1));

        HttpEntity<Void> auth = createAuthRequest(userA.getId(), userA.getEmail());
        ResponseEntity<List<UserChargingProviderResponse>> response = restTemplate.exchange(
                "/api/users/me/charging-providers", HttpMethod.GET, auth,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("IONITY", response.getBody().get(0).providerName());
    }

    // ── Auto-deactivation ─────────────────────────────────────────────────────

    @Test
    void shouldDeactivatePreviousProvider_WhenAddingNew() {
        addProvider(userA, "EnBW", LocalDate.of(2025, 6, 1));

        // Add a new provider starting 2026-01-01 — EnBW should get active_until = 2025-12-31
        addProvider(userA, "IONITY", LocalDate.of(2026, 1, 1));

        HttpEntity<Void> auth = createAuthRequest(userA.getId(), userA.getEmail());
        ResponseEntity<List<UserChargingProviderResponse>> response = restTemplate.exchange(
                "/api/users/me/charging-providers", HttpMethod.GET, auth,
                new ParameterizedTypeReference<>() {});

        List<UserChargingProviderResponse> providers = response.getBody();
        assertNotNull(providers);
        assertEquals(2, providers.size());

        UserChargingProviderResponse ionity = providers.stream()
                .filter(p -> "IONITY".equals(p.providerName())).findFirst().orElseThrow();
        UserChargingProviderResponse enbw = providers.stream()
                .filter(p -> "EnBW".equals(p.providerName())).findFirst().orElseThrow();

        assertNull(ionity.activeUntil());
        assertEquals(LocalDate.of(2025, 12, 31), enbw.activeUntil());
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Test
    void shouldDeleteOwnProvider() {
        UserChargingProviderResponse created = addProvider(userA, "IONITY", LocalDate.of(2026, 1, 1));

        HttpEntity<Void> auth = createAuthRequest(userA.getId(), userA.getEmail());
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/users/me/charging-providers/" + created.id(),
                HttpMethod.DELETE, auth, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        ResponseEntity<List<UserChargingProviderResponse>> getResponse = restTemplate.exchange(
                "/api/users/me/charging-providers", HttpMethod.GET, auth,
                new ParameterizedTypeReference<>() {});
        assertTrue(getResponse.getBody().isEmpty());
    }

    @Test
    void shouldReturn400_WhenDeletingOtherUsersProvider() {
        UserChargingProviderResponse userBProvider = addProvider(userB, "Fastned", LocalDate.of(2026, 1, 1));

        // User A tries to delete User B's provider
        HttpEntity<Void> authA = createAuthRequest(userA.getId(), userA.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/users/me/charging-providers/" + userBProvider.id(),
                HttpMethod.DELETE, authA, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void shouldReturn400_WhenProviderNameIsBlank() {
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "", null, null, null, null, LocalDate.now());
        HttpEntity<UserChargingProviderRequest> auth = createAuthRequest(request, userA.getId(), userA.getEmail());

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/users/me/charging-providers", auth, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturn400_WhenActiveDateIsInFuture() {
        UserChargingProviderRequest request = new UserChargingProviderRequest(
                "IONITY", null, null, null, null, LocalDate.now().plusDays(1));
        HttpEntity<UserChargingProviderRequest> auth = createAuthRequest(request, userA.getId(), userA.getEmail());

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/users/me/charging-providers", auth, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UserChargingProviderRequest buildRequest(String providerName, LocalDate activeFrom) {
        return new UserChargingProviderRequest(
                providerName,
                new BigDecimal("0.29"),
                new BigDecimal("0.49"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                activeFrom
        );
    }

    private UserChargingProviderResponse addProvider(User user, String providerName, LocalDate activeFrom) {
        UserChargingProviderRequest request = buildRequest(providerName, activeFrom);
        HttpEntity<UserChargingProviderRequest> auth = createAuthRequest(request, user.getId(), user.getEmail());
        ResponseEntity<UserChargingProviderResponse> response = restTemplate.postForEntity(
                "/api/users/me/charging-providers", auth, UserChargingProviderResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "addProvider failed for " + providerName);
        return response.getBody();
    }
}
