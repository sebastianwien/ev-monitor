package com.evmonitor.infrastructure.web;

import com.evmonitor.application.routetype.RouteTypeBackfillJob;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    void admin_startsDryRunByDefault_andReadsTheResult() throws InterruptedException {
        User admin = createAndSaveAdminUser("rt-admin-" + System.nanoTime() + "@example.com");
        // Mindestens ein Kandidat ohne Streckentyp, damit der Lauf wirklich rechnet
        Car car = createAndSaveCar(admin.getId(), CarBrand.CarModel.MODEL_3);
        for (int h : new int[]{7, 20}) {
            evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("30.0"), null, 60, "u33d1", 10_000 + h,
                    new BigDecimal("11.0"), new BigDecimal("80"), LocalDateTime.of(2026, 3, 1, h, 0), DataSource.WALLBOX_GOE, ChargingType.AC, null));
        }

        ResponseEntity<RouteTypeBackfillJob.Status> started = restTemplate.exchange("/api/admin/route-type/backfill", HttpMethod.POST,
                createAuthRequest(admin.getId(), admin.getEmail()), RouteTypeBackfillJob.Status.class);
        assertEquals(HttpStatus.ACCEPTED, started.getStatusCode());

        // Lauf ist asynchron; bis zu 10 s auf das Ergebnis warten
        RouteTypeBackfillJob.Status body = null;
        for (int i = 0; i < 100; i++) {
            ResponseEntity<RouteTypeBackfillJob.Status> status = restTemplate.exchange("/api/admin/route-type/backfill", HttpMethod.GET,
                    createAuthRequest(admin.getId(), admin.getEmail()), RouteTypeBackfillJob.Status.class);
            assertEquals(HttpStatus.OK, status.getStatusCode());
            body = status.getBody();
            if (body != null && !body.running() && body.last() != null) break;
            Thread.sleep(100);
        }
        assertNotNull(body);
        assertFalse(body.running(), "Status: " + body);
        assertNotNull(body.last(), "Status: " + body);
        assertTrue(body.last().dryRun());
        assertTrue(body.last().candidates() >= 2, "Status: " + body);
        assertEquals(0, body.last().errors(), "Status: " + body);
    }
}
