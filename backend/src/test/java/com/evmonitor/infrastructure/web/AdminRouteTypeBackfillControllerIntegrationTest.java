package com.evmonitor.infrastructure.web;

import com.evmonitor.application.routetype.RouteTypeBackfillJob;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class AdminRouteTypeBackfillControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("rt-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/route-type/backfill", HttpMethod.POST,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void admin_startsDryRunByDefault_andReadsTheResult() {
        User admin = createAndSaveAdminUser("rt-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<RouteTypeBackfillJob.Status> started = restTemplate.exchange("/api/admin/route-type/backfill", HttpMethod.POST,
                createAuthRequest(admin.getId(), admin.getEmail()), RouteTypeBackfillJob.Status.class);
        assertEquals(HttpStatus.ACCEPTED, started.getStatusCode());

        // Test-Executor ist synchron: der Lauf ist beim Statusabruf schon fertig
        ResponseEntity<RouteTypeBackfillJob.Status> status = restTemplate.exchange("/api/admin/route-type/backfill", HttpMethod.GET,
                createAuthRequest(admin.getId(), admin.getEmail()), RouteTypeBackfillJob.Status.class);
        assertEquals(HttpStatus.OK, status.getStatusCode());
        assertNotNull(status.getBody());
        assertFalse(status.getBody().running());
        assertNotNull(status.getBody().last());
        assertTrue(status.getBody().last().dryRun());
    }
}
