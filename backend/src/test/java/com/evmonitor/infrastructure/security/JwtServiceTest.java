package com.evmonitor.infrastructure.security;

import com.evmonitor.testutil.JwtTestUtils;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Tests for JwtService.
 * Tests JWT token generation, validation, and claim extraction.
 *
 * SECURITY CRITICAL: JWT is the backbone of authentication.
 * Any bug here = broken auth = major security vulnerability!
 */
class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET = "test-secret-key-FOR-TESTING-ONLY-MIN-64-CHARS-LONG-HS512-ALGORITHM-1234567890123456789012345678901234567890";
    private static final long TEST_EXPIRATION_MS = 3600000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationInMs", TEST_EXPIRATION_MS);
    }

    @Test
    void shouldGenerateValidToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        com.evmonitor.domain.User testUser = com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(userId, email, "dummy-hash");
        UserDetails userPrincipal = UserPrincipal.create(testUser);

        // When
        String token = jwtService.generateToken(userPrincipal);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts: header.payload.signature
    }

    @Test
    void shouldExtractUsernameFromToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        com.evmonitor.domain.User testUser = com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(userId, email, "dummy-hash");
        UserDetails userPrincipal = UserPrincipal.create(testUser);
        String token = jwtService.generateToken(userPrincipal);

        // When
        String extractedEmail = jwtService.extractUsername(token);

        // Then
        assertEquals(email, extractedEmail);
    }

    @Test
    void shouldValidateValidToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        com.evmonitor.domain.User testUser = com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(userId, email, "dummy-hash");
        UserDetails userPrincipal = UserPrincipal.create(testUser);
        String token = jwtService.generateToken(userPrincipal);

        // When
        boolean isValid = jwtService.isTokenValid(token, userPrincipal);

        // Then
        assertTrue(isValid);
    }

    @Test
    void shouldRejectExpiredToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String expiredToken = JwtTestUtils.generateExpiredToken(userId, email);

        // When & Then
        assertThrows(ExpiredJwtException.class, () -> {
            jwtService.extractUsername(expiredToken);
        });
    }

    @Test
    void shouldRejectTokenWithWrongSignature() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String tokenWithWrongSignature = JwtTestUtils.generateTokenWithWrongSignature(userId, email);

        // When & Then
        assertThrows(SignatureException.class, () -> {
            jwtService.extractUsername(tokenWithWrongSignature);
        });
    }

    @Test
    void shouldRejectMalformedToken() {
        // Given
        String malformedToken = JwtTestUtils.generateMalformedToken();

        // When & Then
        assertThrows(Exception.class, () -> {
            jwtService.extractUsername(malformedToken);
        });
    }

    @Test
    void shouldRejectTokenForDifferentUser() {
        // Given
        UUID userId1 = UUID.randomUUID();
        String email1 = "user1@example.com";
        com.evmonitor.domain.User testUser1 = com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(userId1, email1, "dummy-hash");
        UserDetails userPrincipal1 = UserPrincipal.create(testUser1);
        String token = jwtService.generateToken(userPrincipal1);

        // Different user tries to use the token
        UUID userId2 = UUID.randomUUID();
        String email2 = "user2@example.com";
        com.evmonitor.domain.User testUser2 = com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(userId2, email2, "dummy-hash");
        UserDetails userPrincipal2 = UserPrincipal.create(testUser2);

        // When
        boolean isValid = jwtService.isTokenValid(token, userPrincipal2);

        // Then
        assertFalse(isValid, "Token should be invalid for different user");
    }

    @Test
    void shouldExtractExpirationDate() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        com.evmonitor.domain.User testUser = com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(userId, email, "dummy-hash");
        UserDetails userPrincipal = UserPrincipal.create(testUser);
        String token = jwtService.generateToken(userPrincipal);

        // When
        java.util.Date expiration = jwtService.extractClaim(token, io.jsonwebtoken.Claims::getExpiration);

        // Then
        assertNotNull(expiration);
        assertTrue(expiration.after(new java.util.Date())); // Should be in the future
    }

    @Test
    void shouldIncludeRegisteredAtClaimInToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        com.evmonitor.domain.User testUser = com.evmonitor.testutil.TestDataBuilder.createTestUserWithId(userId, email, "dummy-hash");
        UserDetails userPrincipal = UserPrincipal.create(testUser);

        // When
        String token = jwtService.generateToken(userPrincipal);
        String registeredAt = jwtService.extractClaim(token, claims -> claims.get("registeredAt", String.class));

        // Then
        assertNotNull(registeredAt, "registeredAt claim must be present in JWT");
        assertEquals(LocalDate.now().toString(), registeredAt);
    }
}
