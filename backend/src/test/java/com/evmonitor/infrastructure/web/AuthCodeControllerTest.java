package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCodeControllerTest extends AbstractIntegrationTest {

    @Test
    void requestAnswersTheSameForUnknownAddresses() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/code/request",
                Map.of("email", "unbekannt-" + System.nanoTime() + "@example.com", "locale", "de"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("SENT");
    }

    @Test
    void invalidAddressIsRejected() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/code/request",
                Map.of("email", "kein-mail"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void codeWithoutRequestIs400InvalidCode() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/code/verify",
                Map.of("email", "nie-angefordert-" + System.nanoTime() + "@example.com", "code", "123456"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_CODE");
    }

    @Test
    void malformedCodeIsRejectedBeforeLookup() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/code/verify",
                Map.of("email", "a@example.com", "code", "12ab"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
