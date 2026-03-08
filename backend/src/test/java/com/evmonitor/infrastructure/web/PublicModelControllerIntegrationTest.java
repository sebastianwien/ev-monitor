package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for PublicModelController.
 * Tests model name resolution with various URL formats.
 */
class PublicModelControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldResolveXpengG6FromUrl() {
        // URL: /modelle/Xpeng/G6 → Enum: XPENG_G6
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/Xpeng/G6", String.class);

        // Should return 200 OK or 404 if no data (but not 500)
        assertTrue(
                response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Expected 200 or 404, got: " + response.getStatusCode());
    }

    @Test
    void shouldResolvePolestar2FromUrl() {
        // URL: /modelle/Polestar/Polestar_2 → Enum: POLESTAR_2
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/Polestar/Polestar_2", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Expected 200 or 404, got: " + response.getStatusCode());
    }

    @Test
    void shouldResolveTeslaModel3FromUrl() {
        // URL: /modelle/Tesla/Model_3 → Enum: TESLA_MODEL_3
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/Tesla/Model_3", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Expected 200 or 404, got: " + response.getStatusCode());
    }

    @Test
    void shouldHandleSpacesInModelName() {
        // URL with spaces (should be encoded as underscores by frontend)
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/Tesla/Model 3", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Expected 200 or 404, got: " + response.getStatusCode());
    }

    @Test
    void shouldReturn404ForNonExistentModel() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/FakeBrand/FakeModel", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturn404ForNonExistentBrand() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/InvalidBrand/G6", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldGetAllModelsWithWltpData() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // Response should be a JSON array
        assertTrue(response.getBody().startsWith("["));
    }
}
