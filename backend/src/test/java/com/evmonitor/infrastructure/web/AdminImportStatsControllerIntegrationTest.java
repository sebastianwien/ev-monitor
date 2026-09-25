package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ingest.event.ImportEventOutcome;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminImportStatsControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired ImportEventRepository importEventRepository;

    @AfterEach
    void clean() {
        importEventRepository.deleteAll();
    }

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("imports-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/stats/imports", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_getsAggregatesWithoutUserOrCarIds() {
        User admin = createAndSaveAdminUser("imports-admin-" + System.nanoTime() + "@example.com");
        UUID userId = UUID.randomUUID();
        UUID carId = UUID.randomUUID();
        importEventRepository.save(ImportEvent.of(DataSource.XPENG_IMPORT, userId, carId)
                .outcome(ImportEventOutcome.PARSE_ERROR).error("XpengParseException: kaputt")
                .createdAt(LocalDateTime.now()).build());

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/stats/imports?days=7", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody())
                .contains("\"provider\":\"XPENG\"", "\"health\":\"ERROR\"", "XpengParseException: kaputt")
                .doesNotContain(userId.toString(), carId.toString(), "userId", "carId");
    }

    @Test
    void daysOutsideRetention_isBadRequest() {
        User admin = createAndSaveAdminUser("imports-admin-" + System.nanoTime() + "@example.com");

        for (String days : new String[]{"0", "91"}) {
            ResponseEntity<String> res = restTemplate.exchange("/api/admin/stats/imports?days=" + days, HttpMethod.GET,
                    createAuthRequest(admin.getId(), admin.getEmail()), String.class);
            assertThat(res.getStatusCode()).as("days=" + days).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
