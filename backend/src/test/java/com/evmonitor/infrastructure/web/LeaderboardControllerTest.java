package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardControllerTest extends AbstractIntegrationTest {

    // Die Ticker-Abfragen selbst sind Postgres-SQL und laufen auf H2 nicht; der Inhalt ist im
    // Service-Test abgedeckt. Hier nur die Monatsprüfung, die vor jeder Abfrage greift.

    @Test
    void ticker_rejectsMonthBeforePreviousMonth() {
        String twoMonthsAgo = YearMonth.now().minusMonths(2).toString();

        ResponseEntity<String> response = restTemplate.getForEntity("/api/public/leaderboard/ticker?month=" + twoMonthsAgo, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ticker_rejectsUnparsableMonth() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/public/leaderboard/ticker?month=Oktober", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
