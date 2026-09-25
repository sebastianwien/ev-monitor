package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Verbindungs-Gesundheit im Admin-Tab "Importe" (R2e). Connectors läuft im Test nicht, also Rückfall. */
class AdminConnectionHealthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/admin/stats/imports/connections";

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("connections-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange(PATH, HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_connectorsUnreachable_getsUnavailableInsteadOfError() {
        User admin = createAndSaveAdminUser("connections-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange(PATH, HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"available\":false", "\"providers\":[]");
    }
}
