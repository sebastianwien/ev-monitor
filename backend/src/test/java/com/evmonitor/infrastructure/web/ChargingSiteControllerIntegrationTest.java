package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Zuletzt genutzte Standorte sind Nutzerdaten - ohne Anmeldung gibt es sie nicht. */
class ChargingSiteControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void suggestionRequiresAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/charging-sites/suggestion?lat=52.52&lon=13.4", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void knownPlacesRequireAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/charging-sites/known", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void recentSitesRequireAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/charging-sites/recent", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
