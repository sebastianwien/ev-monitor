package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TickerControllerTest extends AbstractIntegrationTest {

    @Test
    void personalTicker_requiresJwt() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/ticker/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void todayTicker_requiresJwt() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/ticker/today", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void personalTicker_userWithoutLogs_getsEmptyList() {
        User user = createAndSaveUser("ticker-me-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");
        // Die Ranking-Queries sind Postgres-SQL und laufen auf H2 nicht; der Rang ist im Service-Test abgedeckt.
        userRepository.setLeaderboardVisible(user.getId(), false);

        ResponseEntity<String> response = restTemplate.exchange("/api/ticker/me", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void tickerShareCharges_canBeSwitchedOff() {
        User user = createAndSaveUser("ticker-share-" + UUID.randomUUID().toString().substring(0, 8) + "@ev-monitor.net");

        ResponseEntity<Void> response = restTemplate.exchange("/api/users/me/ticker-share-charges?enabled=false",
                HttpMethod.PUT, createAuthRequest(user.getId(), user.getEmail()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.isTickerShareCharges(user.getId())).isFalse();
    }

    @Test
    void tickerShareCharges_requiresJwt() {
        ResponseEntity<Void> response = restTemplate.exchange("/api/users/me/ticker-share-charges?enabled=false",
                HttpMethod.PUT, HttpEntity.EMPTY, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
