package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GlobalExceptionHandler.
 *
 * Verifies that common client errors return proper 4xx responses
 * instead of falling through to the generic 500 handler.
 */
class GlobalExceptionHandlerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void formUrlencoded_returns415_withHelpfulMessage() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("email=test%40test.com&password=test", headers),
                Map.class);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().get("code"));
    }

    @Test
    void unsupportedHttpMethod_returns405() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/login", HttpMethod.DELETE,
                HttpEntity.EMPTY, Map.class);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().get("code"));
    }
}
