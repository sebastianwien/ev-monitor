package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.security.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoAccountInterceptorTest {

    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final DemoAccountInterceptor interceptor = new DemoAccountInterceptor(rateLimitService);

    @BeforeEach
    void allowRateLimitByDefault() {
        when(rateLimitService.tryConsumeDemoRequest(any())).thenReturn(true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsDemo() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("demo", null,
                        List.of(new SimpleGrantedAuthority("DEMO"))));
    }

    private void authenticateAsRegularUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private boolean preHandle(String method, String uri, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        return interceptor.preHandle(request, response, new Object());
    }

    @Test
    void optionsIsAlwaysAllowed() throws Exception {
        authenticateAsDemo();
        assertTrue(preHandle("OPTIONS", "/api/users/me/export", new MockHttpServletResponse()));
    }

    @Test
    void nonDemoSessionIsNeverRestricted() throws Exception {
        authenticateAsRegularUser();
        assertTrue(preHandle("POST", "/api/logs", new MockHttpServletResponse()));
        assertTrue(preHandle("GET", "/api/users/me/export", new MockHttpServletResponse()));
    }

    @Test
    void unauthenticatedRequestPassesThrough() throws Exception {
        assertTrue(preHandle("POST", "/api/logs", new MockHttpServletResponse()));
    }

    @Test
    void demoMayReadWhitelistedPaths() throws Exception {
        authenticateAsDemo();
        assertTrue(preHandle("GET", "/api/logs", new MockHttpServletResponse()));
        assertTrue(preHandle("GET", "/api/logs/statistics", new MockHttpServletResponse()));
        assertTrue(preHandle("GET", "/api/trips", new MockHttpServletResponse()));
        assertTrue(preHandle("GET", "/api/cars/123/soh", new MockHttpServletResponse()));
        assertTrue(preHandle("GET", "/api/users/me/stats", new MockHttpServletResponse()));
        assertTrue(preHandle("GET", "/api/public/leaderboard/ticker", new MockHttpServletResponse()));
    }

    @Test
    void demoCannotWrite() throws Exception {
        authenticateAsDemo();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = preHandle("POST", "/api/logs", response);

        assertFalse(proceed);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("DEMO_ACCOUNT_READONLY"));
    }

    @Test
    void demoCannotReadNonWhitelistedPaths() throws Exception {
        authenticateAsDemo();
        // Sensitive sibling under an otherwise-allowed prefix must stay closed.
        MockHttpServletResponse export = new MockHttpServletResponse();
        assertFalse(preHandle("GET", "/api/users/me/export", export));
        assertEquals(403, export.getStatus());

        assertFalse(preHandle("GET", "/api/subscription/status", new MockHttpServletResponse()));
        assertFalse(preHandle("GET", "/api/user/api-keys", new MockHttpServletResponse()));
        assertFalse(preHandle("GET", "/api/imports/xpeng/connections", new MockHttpServletResponse()));
        assertFalse(preHandle("GET", "/api/users/me/charging-providers", new MockHttpServletResponse()));
        assertFalse(preHandle("GET", "/api/tax-export/csv", new MockHttpServletResponse()));
    }

    @Test
    void demoRequestsAreRateLimitedPerIp() throws Exception {
        authenticateAsDemo();
        when(rateLimitService.tryConsumeDemoRequest(any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = preHandle("GET", "/api/logs", response);

        assertFalse(proceed);
        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("DEMO_RATE_LIMITED"));
    }
}
