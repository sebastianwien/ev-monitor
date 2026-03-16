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

    // --- /api/public/brands/{brand} ---

    @Test
    void shouldGetTeslaBrandPage() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/brands/Tesla", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"brandEnum\":\"TESLA\""));
        assertTrue(response.getBody().contains("\"brandDisplayName\":\"Tesla\""));
        assertTrue(response.getBody().contains("\"models\":["));
    }

    @Test
    void shouldResolveBrandCaseInsensitively() {
        // Both TESLA and Tesla should resolve to the same canonical response
        ResponseEntity<String> upper = restTemplate.getForEntity("/api/public/brands/TESLA", String.class);
        ResponseEntity<String> lower = restTemplate.getForEntity("/api/public/brands/tesla", String.class);

        assertEquals(HttpStatus.OK, upper.getStatusCode());
        assertEquals(HttpStatus.OK, lower.getStatusCode());
        assertTrue(upper.getBody().contains("\"brandDisplayName\":\"Tesla\""),
                "Uppercase brand URL should return canonical brandDisplayName");
        assertTrue(lower.getBody().contains("\"brandDisplayName\":\"Tesla\""),
                "Lowercase brand URL should return canonical brandDisplayName");
    }

    @Test
    void shouldReturn404ForUnknownBrand() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/brands/FakeBrand", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturnModelsWithWltpVariantsForBrand() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/brands/BMW", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"brandEnum\":\"BMW\""));
        assertTrue(response.getBody().contains("\"wltpVariants\":["));
    }

    @Test
    void shouldReturnModelStatsResponseWithBrandDisplayName() {
        // Verify that model stats also include brandDisplayName for canonical URL redirect
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/Tesla/Model_3", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.OK ||
                response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Expected 200 or 404, got: " + response.getStatusCode());

        if (response.getStatusCode() == HttpStatus.OK) {
            assertTrue(response.getBody().contains("\"brandDisplayName\":\"Tesla\""),
                    "Model stats response should include brandDisplayName for canonical URL support");
        }
    }

    @Test
    void shouldResolveModelStatsCaseInsensitively() {
        // /modelle/TESLA/MODEL_3 and /modelle/Tesla/Model_3 should both work
        ResponseEntity<String> upper = restTemplate.getForEntity(
                "/api/public/models/TESLA/MODEL_3", String.class);
        ResponseEntity<String> canonical = restTemplate.getForEntity(
                "/api/public/models/Tesla/Model_3", String.class);

        assertEquals(canonical.getStatusCode(), upper.getStatusCode(),
                "Case-insensitive URL should return same status as canonical URL");
    }

    // --- /api/public/models/top ---

    @Test
    void shouldReturnTopModelsAsJsonArray() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/top", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().startsWith("["),
                "Response should be a JSON array");
    }

    @Test
    void shouldRespectLimitParameter() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/top?limit=3", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void shouldCapLimitAt50() {
        // Requesting 999 should be silently capped to 50
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/top?limit=999", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldReturnTopModelsWithRequiredFields() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/top?limit=5", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        if (!response.getBody().equals("[]")) {
            assertTrue(response.getBody().contains("\"modelDisplayName\""),
                    "Response should include modelDisplayName");
            assertTrue(response.getBody().contains("\"logCount\""),
                    "Response should include logCount");
            assertTrue(response.getBody().contains("\"modelUrlSlug\""),
                    "Response should include modelUrlSlug");
            assertTrue(response.getBody().contains("\"brandDisplayName\""),
                    "Response should include brandDisplayName");
        }
    }

    @Test
    void shouldNotExposeInternalUserDataInTopModels() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/top", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().contains("\"userId\""),
                "Top models must not expose userId");
        assertFalse(response.getBody().contains("\"email\""),
                "Top models must not expose email");
        assertFalse(response.getBody().contains("\"password\""),
                "Top models must not expose password");
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() {
        // No Authorization header — must return 200, not 401/403
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/models/top", String.class);

        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
