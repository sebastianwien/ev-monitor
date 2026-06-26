package com.evmonitor.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isDemoToken(jwt)) {
                    authenticateDemo(jwt, request);
                } else {
                    authenticateRegular(jwt, request);
                }
            }
        } catch (JwtException | IllegalArgumentException e) {
            // Malformed/expired token or bad userId claim — leave the request unauthenticated.
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateRegular(String jwt, HttpServletRequest request) {
        String userEmail = jwtService.extractUsername(jwt);
        if (userEmail == null) {
            return;
        }
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(userEmail);
        } catch (UsernameNotFoundException e) {
            // User no longer exists (e.g. email was changed) — treat as unauthenticated
            return;
        }
        if (jwtService.isTokenValid(jwt, userDetails)) {
            setAuthentication(userDetails, userDetails.getAuthorities(), request);
        }
    }

    private void authenticateDemo(String jwt, HttpServletRequest request) {
        if (!jwtService.isDemoTokenValid(jwt)) {
            return;
        }
        UUID userId = jwtService.extractUserId(jwt);
        if (userId == null) {
            return;
        }
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserById(userId);
        } catch (UsernameNotFoundException e) {
            return;
        }
        // Minimal authorities: only DEMO. The demo session must NOT inherit the underlying
        // account's role (e.g. ADMIN) — that keeps privileged endpoints closed regardless of
        // which account the demo points to. Read-only is enforced by DemoAccountInterceptor.
        setAuthentication(userDetails, List.of(new SimpleGrantedAuthority("DEMO")), request);
    }

    private void setAuthentication(UserDetails userDetails,
                                   Collection<? extends GrantedAuthority> authorities,
                                   HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
