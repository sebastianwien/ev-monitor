package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Zuletzt genutzte Standorte sind Nutzerdaten - ohne Anmeldung gibt es sie nicht. */
class ChargingSiteControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void recentSitesRequireAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/charging-sites/recent", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void questionsRequireAuthentication() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                restTemplate.getForEntity("/api/charging-sites/questions", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.postForEntity(
                "/api/charging-sites/questions/" + java.util.UUID.randomUUID() + "/dismiss", null, String.class).getStatusCode());
    }

    @Test
    void answerValidatesTheNameAndHidesForeignQuestions() {
        var user = createAndSaveUser("site-ctrl-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        String url = "/api/charging-sites/questions/" + java.util.UUID.randomUUID() + "/answer";

        assertEquals(HttpStatus.BAD_REQUEST, restTemplate.exchange(url, HttpMethod.POST,
                createAuthRequest(Map.of("name", " "), user.getId(), user.getEmail()), String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, restTemplate.exchange(url, HttpMethod.POST,
                createAuthRequest(Map.of("name", "x".repeat(101)), user.getId(), user.getEmail()), String.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, restTemplate.exchange(url, HttpMethod.POST,
                createAuthRequest(Map.of("name", "Supermarkt"), user.getId(), user.getEmail()), String.class).getStatusCode());
    }
}
