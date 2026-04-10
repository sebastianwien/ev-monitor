package com.evmonitor.infrastructure.security;

import com.evmonitor.application.publicapi.ApiKeyService;
import com.evmonitor.domain.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates requests to /api/v1/** using API keys.
 * Expects: Authorization: Bearer evm_<key>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_V1_PATH = "/api/v1/";
    private static final String KEY_PREFIX = "evm_";

    private final ApiKeyService apiKeyService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith(API_V1_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Already authenticated (e.g. by JWT filter) — skip
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer " + KEY_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"API Key erforderlich\"}");
            return;
        }

        String plaintext = authHeader.substring("Bearer ".length());
        Optional<ApiKey> apiKey = apiKeyService.validateKey(plaintext);

        if (apiKey.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Ungültiger API Key\"}");
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserById(apiKey.get().getUserId());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Store key ID and key object as request attributes for rate limiting and feature flags
        request.setAttribute("apiKeyId", apiKey.get().getId().toString());
        request.setAttribute("apiKey", apiKey.get());

        filterChain.doFilter(request, response);
    }
}
