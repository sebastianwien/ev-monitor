package com.evmonitor.infrastructure.security;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private User user(UUID id, String email, String role) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(id).email(email).username("Ihle").passwordHash("hash")
                .authProvider(AuthProvider.LOCAL).role(role)
                .emailVerified(true).createdAt(now).updatedAt(now)
                .build();
    }

    private Authentication runFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void demoTokenResolvesByIdAndGrantsOnlyDemoAuthority() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(jwtService.isDemoToken("demo")).thenReturn(true);
        when(jwtService.isDemoTokenValid("demo")).thenReturn(true);
        when(jwtService.extractUserId("demo")).thenReturn(ownerId);
        when(userDetailsService.loadUserById(ownerId))
                .thenReturn(UserPrincipal.create(user(ownerId, "owner@example.com", "USER")));

        Authentication auth = runFilter("demo");

        assertNotNull(auth);
        assertTrue(hasAuthority(auth, "DEMO"));
        assertFalse(hasAuthority(auth, "ROLE_USER"), "demo must not inherit the account role");
    }

    @Test
    void demoTokenDoesNotInheritAdminRole() throws Exception {
        UUID ownerId = UUID.randomUUID();
        when(jwtService.isDemoToken("demo")).thenReturn(true);
        when(jwtService.isDemoTokenValid("demo")).thenReturn(true);
        when(jwtService.extractUserId("demo")).thenReturn(ownerId);
        when(userDetailsService.loadUserById(ownerId))
                .thenReturn(UserPrincipal.create(user(ownerId, "admin@example.com", "ADMIN")));

        Authentication auth = runFilter("demo");

        assertNotNull(auth);
        assertTrue(hasAuthority(auth, "DEMO"));
        assertFalse(hasAuthority(auth, "ROLE_ADMIN"), "demo must never gain admin authority");
    }

    @Test
    void regularTokenResolvesByEmailWithAccountAuthorities() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.isDemoToken("real")).thenReturn(false);
        when(jwtService.extractUsername("real")).thenReturn("user@example.com");
        UserPrincipal principal = UserPrincipal.create(user(id, "user@example.com", "USER"));
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid("real", principal)).thenReturn(true);

        Authentication auth = runFilter("real");

        assertNotNull(auth);
        assertTrue(hasAuthority(auth, "ROLE_USER"));
        assertFalse(hasAuthority(auth, "DEMO"));
    }

    @Test
    void missingHeaderLeavesUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
    }

    private boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
