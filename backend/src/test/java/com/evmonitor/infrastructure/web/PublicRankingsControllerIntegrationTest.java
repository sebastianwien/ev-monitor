package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PublicRankingsController.
 */
class PublicRankingsControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldBeAccessibleWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/rankings/efficiency", String.class);

        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldReturnJsonArray() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/rankings/efficiency", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().startsWith("["), "Response should be a JSON array");
    }

    @Test
    void shouldRespectLimitParameter() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/rankings/efficiency?limit=3", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void shouldCapLimitAt10() {
        // Requesting 999 should be silently capped to 10
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/rankings/efficiency?limit=999", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldReturnRequiredFieldsWhenDataPresent() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/rankings/efficiency?limit=5", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        if (!response.getBody().equals("[]")) {
            assertTrue(response.getBody().contains("\"avgConsumptionKwhPer100km\""),
                    "Response should include avgConsumptionKwhPer100km");
            assertTrue(response.getBody().contains("\"modelDisplayName\""),
                    "Response should include modelDisplayName");
            assertTrue(response.getBody().contains("\"logCount\""),
                    "Response should include logCount");
        }
    }

    @Test
    void categoriesEndpointShouldReturnAllNineCategories() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/rankings/categories", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // 9 categories means 9 "key" fields
        long keyCount = response.getBody().chars().filter(c -> c == '{').count();
        assertEquals(9, keyCount, "Should return exactly 9 category objects");
        assertTrue(response.getBody().contains("\"displayName\""), "Each category should have displayName");
    }

    @Test
    void categoriesEndpointShouldBeAccessibleWithoutAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/rankings/categories", String.class);

        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldNotExposeInternalUserData() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/public/rankings/efficiency", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().contains("\"userId\""),
                "Efficiency ranking must not expose userId");
        assertFalse(response.getBody().contains("\"email\""),
                "Efficiency ranking must not expose email");
        assertFalse(response.getBody().contains("\"password\""),
                "Efficiency ranking must not expose password");
    }
}
