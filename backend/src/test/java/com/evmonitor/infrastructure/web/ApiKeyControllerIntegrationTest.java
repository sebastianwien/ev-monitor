package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GET/POST/DELETE /api/user/api-keys.
 *
 * Covers: key creation (no plaintext in list), listing, deletion (revoke).
 */
class ApiKeyControllerIntegrationTest extends AbstractIntegrationTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("apikey-ctrl-" + System.nanoTime() + "@ev-monitor.net");
    }

    private HttpEntity<String> createJsonAuthRequest(String jsonBody) {
        HttpHeaders headers = createAuthHeaders(user.getId(), user.getEmail());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(jsonBody, headers);
    }

    @Test
    void createKey_returnsPlaintextOnce() {
        HttpEntity<String> request = createJsonAuthRequest("{\"name\": \"Test Key\"}");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/user/api-keys", HttpMethod.POST, request, Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("plaintextKey"));
        String key = (String) response.getBody().get("plaintextKey");
        assertTrue(key.startsWith("evm_"), "Key should start with evm_");
        assertNotNull(response.getBody().get("keyPrefix"));
        assertNotNull(response.getBody().get("id"));
    }

    @Test
    void listKeys_doesNotIncludePlaintext() {
        // Create a key first
        HttpEntity<String> createRequest = createJsonAuthRequest("{\"name\": \"My Key\"}");
        restTemplate.exchange("/api/user/api-keys", HttpMethod.POST, createRequest, Map.class);

        // List keys
        HttpEntity<Void> listRequest = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<List> response = restTemplate.exchange(
                "/api/user/api-keys", HttpMethod.GET, listRequest, List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().isEmpty());

        Map<String, Object> key = (Map<String, Object>) response.getBody().getFirst();
        assertFalse(key.containsKey("plaintextKey"), "Plaintext key must NOT appear in list");
        assertFalse(key.containsKey("keyHash"), "Key hash must NOT appear in list");
        assertTrue(key.containsKey("keyPrefix"));
        assertTrue(key.containsKey("id"));
        assertTrue(key.containsKey("name"));
    }

    @Test
    void deleteKey_revokesAccess() {
        // Create key and get its ID
        HttpEntity<String> createRequest = createJsonAuthRequest("{\"name\": \"Key to delete\"}");
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/user/api-keys", HttpMethod.POST, createRequest, Map.class);

        String keyId = (String) created.getBody().get("id");
        String plaintextKey = (String) created.getBody().get("plaintextKey");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        // Delete the key
        HttpEntity<Void> deleteRequest = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/user/api-keys/" + keyId, HttpMethod.DELETE, deleteRequest, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // Attempt to use deleted key → 401
        HttpHeaders apiHeaders = new HttpHeaders();
        apiHeaders.set("Authorization", "Bearer " + plaintextKey);
        apiHeaders.setContentType(MediaType.APPLICATION_JSON);
        String sessionBody = """
                { "car_id": "%s", "sessions": [{ "date": "2024-11-15T14:30:00", "kwh": 10.0 }] }
                """.formatted(car.getId());
        ResponseEntity<Map> uploadResponse = restTemplate.exchange(
                "/api/v1/sessions", HttpMethod.POST,
                new HttpEntity<>(sessionBody, apiHeaders), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, uploadResponse.getStatusCode());
    }

    @Test
    void cannotDeleteOtherUsersKey() {
        // Create key for user
        HttpEntity<String> createRequest = createJsonAuthRequest("{\"name\": \"User Key\"}");
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/user/api-keys", HttpMethod.POST, createRequest, Map.class);
        String keyId = (String) created.getBody().get("id");

        // Other user tries to delete
        User other = createAndSaveUser("other-" + System.nanoTime() + "@ev-monitor.net");
        HttpEntity<Void> deleteRequest = createAuthRequest(other.getId(), other.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/user/api-keys/" + keyId, HttpMethod.DELETE, deleteRequest, Void.class);

        // Should either 204 (nothing deleted) or 404 — key must still be usable
        // The important thing: key still works after this call
        // Verify it's still in the original user's list
        HttpEntity<Void> listRequest = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/user/api-keys", HttpMethod.GET, listRequest, List.class);
        assertEquals(1, listResponse.getBody().size());
    }
}
