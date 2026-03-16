package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.application.publicapi.ApiKeyCreatedResponse;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PATCH /api/user/api-keys/{id}/merge-sessions.
 *
 * Covers: toggle on/off, ownership check, field in list response.
 */
class ApiKeyMergeSessionsTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    private User user;
    private String keyId;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("merge-apikey-" + System.nanoTime() + "@ev-monitor.net");
        ApiKeyCreatedResponse created = apiKeyService.createKey(user.getId(), "Test Key");
        keyId = created.id().toString();
    }

    @Test
    void testUpdateMergeSessions_success() {
        HttpHeaders headers = createAuthHeaders(user.getId(), user.getEmail());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"mergeSessions\": true}", headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/user/api-keys/" + keyId + "/merge-sessions",
                HttpMethod.PATCH, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("mergeSessions"));
        assertEquals(keyId, response.getBody().get("id").toString());
    }

    @Test
    void testUpdateMergeSessions_toggleOffAgain() {
        // First enable
        HttpHeaders headers = createAuthHeaders(user.getId(), user.getEmail());
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange("/api/user/api-keys/" + keyId + "/merge-sessions",
                HttpMethod.PATCH, new HttpEntity<>("{\"mergeSessions\": true}", headers), Map.class);

        // Then disable
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/user/api-keys/" + keyId + "/merge-sessions",
                HttpMethod.PATCH, new HttpEntity<>("{\"mergeSessions\": false}", headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().get("mergeSessions"));
    }

    @Test
    void testUpdateMergeSessions_wrongOwner_returns404() {
        User other = createAndSaveUser("other-merge-" + System.nanoTime() + "@ev-monitor.net");
        HttpHeaders headers = createAuthHeaders(other.getId(), other.getEmail());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"mergeSessions\": true}", headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/user/api-keys/" + keyId + "/merge-sessions",
                HttpMethod.PATCH, request, Map.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testListKeys_includesMergeSessionsField() {
        HttpEntity<Void> listRequest = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/user/api-keys", HttpMethod.GET, listRequest, List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().isEmpty());

        Map<String, Object> key = (Map<String, Object>) response.getBody().getFirst();
        assertTrue(key.containsKey("mergeSessions"), "mergeSessions field must be present in list response");
        assertEquals(false, key.get("mergeSessions"), "Default should be false");
    }
}
