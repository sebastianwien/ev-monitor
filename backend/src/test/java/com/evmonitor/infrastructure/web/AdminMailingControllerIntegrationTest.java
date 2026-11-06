package com.evmonitor.infrastructure.web;

import com.evmonitor.application.mail.MailType;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMailingControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void nonAdmin_isForbiddenOnAllEndpoints() {
        User user = createAndSaveUser("mailing-user-" + System.nanoTime() + "@example.com");

        for (String path : new String[]{"/api/admin/mailings", "/api/admin/mailings/re-engagement/recent",
                "/api/admin/mailings/re-engagement/preview"}) {
            ResponseEntity<String> res = restTemplate.exchange(path, HttpMethod.GET,
                    createAuthRequest(user.getId(), user.getEmail()), String.class);
            assertThat(res.getStatusCode()).as(path).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void admin_seesEveryCatalogEntry_andAPreview() {
        User admin = createAndSaveAdminUser("mailing-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<Object[]> list = restTemplate.exchange("/api/admin/mailings?days=30", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), Object[].class);
        ResponseEntity<String> preview = restTemplate.exchange("/api/admin/mailings/re-engagement/preview", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(MailType.values().length);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getBody()).contains("\"html\"").contains("Max");
    }

    @Test
    void unknownMailing_isNotFound() {
        User admin = createAndSaveAdminUser("mailing-admin-404-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/mailings/nope/preview", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
