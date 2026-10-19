package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Im Test ist kein Service-Account konfiguriert: Tab zeigt den Leerzustand, Sync wird abgelehnt. */
class AdminSeoControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("seo-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/seo/overview", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_getsStatusOverviewPagesAndQueries() {
        User admin = createAndSaveAdminUser("seo-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> status = restTemplate.exchange("/api/admin/seo/status", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody()).contains("\"enabled\":false").contains("\"runs\":[");

        ResponseEntity<String> overview = restTemplate.exchange("/api/admin/seo/overview?days=7", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overview.getBody()).contains("\"periodDays\":7").contains("\"days\":[");

        ResponseEntity<String> pages = restTemplate.exchange("/api/admin/seo/pages?days=28&filter=UNDERRATED", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(pages.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> queries = restTemplate.exchange("/api/admin/seo/queries?days=90&brand=NON_BRAND", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(queries.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> models = restTemplate.exchange("/api/admin/seo/models?days=28&level=BRAND&filter=CONTENT_GAP", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(models.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(models.getBody()).startsWith("[");
    }

    @Test
    void admin_invalidPeriodOrFilterIsBadRequest() {
        User admin = createAndSaveAdminUser("seo-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> period = restTemplate.exchange("/api/admin/seo/overview?days=10", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(period.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> filter = restTemplate.exchange("/api/admin/seo/pages?filter=HACK", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(filter.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void admin_syncWithoutKeyIsConflict() {
        User admin = createAndSaveAdminUser("seo-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/seo/sync?days=30", HttpMethod.POST,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).contains("\"started\":false");
    }
}
