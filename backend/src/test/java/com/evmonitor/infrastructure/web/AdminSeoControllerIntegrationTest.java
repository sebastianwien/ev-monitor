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
    void admin_getsMarketScopedDataDrilldownsBrandTrendAndOpportunities() {
        User admin = createAndSaveAdminUser("seo-admin-" + System.nanoTime() + "@example.com");
        for (String url : new String[] {"/api/admin/seo/overview?days=28&market=DE", "/api/admin/seo/pages?market=INTL",
                "/api/admin/seo/queries?market=DE", "/api/admin/seo/pages/queries?path=/modelle", "/api/admin/seo/queries/pages?query=test",
                "/api/admin/seo/brand-trend?weeks=12", "/api/admin/seo/opportunities?days=7&market=DE"}) {
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, createAuthRequest(admin.getId(), admin.getEmail()), String.class);
            assertThat(res.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<String> blank = restTemplate.exchange("/api/admin/seo/queries/pages?query=", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> weeks = restTemplate.exchange("/api/admin/seo/brand-trend?weeks=500", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(weeks.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

    @Test
    void auditIsAdminOnlyAndRunWithoutBaseUrlIsConflict() {
        User user = createAndSaveUser("seo-user-" + System.nanoTime() + "@example.com");
        User admin = createAndSaveAdminUser("seo-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> forbidden = restTemplate.exchange("/api/admin/seo/audit", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> view = restTemplate.exchange("/api/admin/seo/audit", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(view.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(view.getBody()).contains("\"enabled\":false").contains("\"rows\":[");

        ResponseEntity<String> runForbidden = restTemplate.exchange("/api/admin/seo/audit/run", HttpMethod.POST,
                createAuthRequest(user.getId(), user.getEmail()), String.class);
        assertThat(runForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> run = restTemplate.exchange("/api/admin/seo/audit/run", HttpMethod.POST,
                createAuthRequest(admin.getId(), admin.getEmail()), String.class);
        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
