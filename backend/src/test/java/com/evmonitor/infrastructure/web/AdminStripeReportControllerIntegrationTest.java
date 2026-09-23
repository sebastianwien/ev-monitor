package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AdminStripeReport;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.external.StripeReportClient;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class AdminStripeReportControllerIntegrationTest extends AbstractIntegrationTest {

    /** Never hit Stripe from tests, even when a test-mode key is present in the local env. */
    @MockitoBean
    private StripeReportClient stripeReportClient;

    @Test
    void nonAdmin_isForbidden() {
        User user = createAndSaveUser("stripe-report-user-" + System.nanoTime() + "@example.com");

        ResponseEntity<String> res = restTemplate.exchange("/api/admin/stripe/report", HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void admin_withoutStripeKey_getsNotConfiguredReport() {
        when(stripeReportClient.fetchAll()).thenReturn(Optional.empty());
        User admin = createAndSaveAdminUser("stripe-report-admin-" + System.nanoTime() + "@example.com");

        ResponseEntity<AdminStripeReport> res = restTemplate.exchange("/api/admin/stripe/report?months=999",
                HttpMethod.GET, createAuthRequest(admin.getId(), admin.getEmail()), AdminStripeReport.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertFalse(res.getBody().configured());
        assertEquals(60, res.getBody().months()); // clamped
    }
}
