package com.evmonitor.infrastructure.web;

import com.evmonitor.application.routetype.RouteTypeBackfillSummary;
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
    void admin_getsDryRunSummaryByDefault() {
        User admin = createAndSaveAdminUser("rt-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<RouteTypeBackfillSummary> res = restTemplate.exchange("/api/admin/route-type/backfill", HttpMethod.POST,
                createAuthRequest(admin.getId(), admin.getEmail()), RouteTypeBackfillSummary.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().dryRun());
        assertTrue(res.getBody().candidates() >= 0);
    }
}
