package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

/**
 * Enforces read-only access for demo sessions.
 *
 * <p>A demo session is any request authenticated via a demo token — the
 * {@link com.evmonitor.infrastructure.security.JwtAuthenticationFilter} marks these with the
 * {@code DEMO} authority. Read-only is keyed on this authority (NOT on {@code isSeedData}), so the
 * demo may safely point at a real account without exposing it to mutation.
 *
 * <p>Policy for demo sessions (fail-closed / default-deny):
 * <ul>
 *   <li>OPTIONS — always allowed (CORS preflight).</li>
 *   <li>Any non-GET method — blocked (no data manipulation).</li>
 *   <li>GET — allowed ONLY for explicitly whitelisted paths. Everything else is blocked so that
 *       any newly added endpoint is private to the demo by default until deliberately opened up.</li>
 * </ul>
 * Non-demo requests pass through untouched.
 */
@Component
public class DemoAccountInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH = new AntPathMatcher();

    private final RateLimitService rateLimitService;

    public DemoAccountInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    /**
     * GET paths a demo session may read. Showcase data only — no identity, billing, API keys or
     * connector-account endpoints. Mixed-prefix paths (e.g. {@code /api/users/me/*}) are listed
     * explicitly so sensitive siblings like {@code /me/export} stay closed.
     *
     * <p>NOTE: default-deny only protects new <em>top-level</em> prefixes. A new GET added UNDER a
     * wildcard prefix here ({@code /api/cars/**}, {@code /api/logs/**}, {@code /api/trips/**},
     * {@code /api/coins/**}) is automatically exposed to the demo — vet such endpoints for PII
     * before adding them.
     */
    private static final List<String> DEMO_READ_WHITELIST = List.of(
            "/api/public/**",
            "/api/app/**",
            "/api/geoip/**",
            "/api/charging-provider-tariffs",
            "/api/charging-provider-tariffs/**",
            "/api/vehicle-specifications/**",
            "/api/cars",
            "/api/cars/**",
            "/api/logs",
            "/api/logs/**",
            "/api/trips",
            "/api/trips/**",
            "/api/fixed-costs",
            "/api/coins",
            "/api/coins/**",
            "/api/users/me/stats");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!isDemoSession()) {
            return true;
        }

        // Demo tokens are public — throttle per IP to cap scraping/DoS of the real account's data.
        if (!rateLimitService.tryConsumeDemoRequest(clientIp(request))) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"DEMO_RATE_LIMITED\"}");
            return false;
        }

        boolean allowed = "GET".equalsIgnoreCase(request.getMethod())
                && isWhitelisted(request.getRequestURI());
        if (allowed) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"DEMO_ACCOUNT_READONLY\"}");
        return false;
    }

    /** Real client IP, respecting the X-Forwarded-For header set by nginx. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isDemoSession() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "DEMO".equals(a.getAuthority()));
    }

    private boolean isWhitelisted(String uri) {
        return DEMO_READ_WHITELIST.stream().anyMatch(pattern -> PATH.match(pattern, uri));
    }
}
