package com.evmonitor.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

/**
 * Secures /api/internal/** endpoints with a shared secret token.
 * The Wallbox Service (and other internal services) must send this token
 * as the X-Internal-Token header. Not used for user-facing endpoints.
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    @Value("${internal.token}")
    private String internalToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (token == null || !MessageDigest.isEqual(token.getBytes(), internalToken.getBytes())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\":\"Forbidden\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
