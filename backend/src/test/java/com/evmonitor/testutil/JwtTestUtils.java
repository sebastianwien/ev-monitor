package com.evmonitor.testutil;

import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Test Utilities for generating test tokens.
 */
public class JwtTestUtils {

    private static final String TEST_SECRET = "test-secret-key-FOR-TESTING-ONLY-MIN-64-CHARS-LONG-HS512-ALGORITHM-1234567890123456789012345678901234567890";
    private static final long TEST_EXPIRATION_MS = 3600000L; // 1 hour

    /**
     * Generate a valid JWT token for testing with JwtService.
     */
    public static String generateValidToken(UUID userId, String email, JwtService jwtService) {
        User testUser = TestDataBuilder.createTestUserWithId(userId, email, "dummy-hash");
        UserPrincipal userPrincipal = UserPrincipal.create(testUser);
        return jwtService.generateToken(userPrincipal);
    }

    /**
     * Generate an expired JWT token for testing.
     * Useful for testing token expiration validation.
     */
    public static String generateExpiredToken(UUID userId, String email) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .issuedAt(new Date(System.currentTimeMillis() - 7200000)) // Issued 2 hours ago
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // Expired 1 hour ago
                .signWith(key)
                .compact();
    }

    /**
     * Generate a token with wrong signature for testing.
     * Useful for testing signature validation.
     */
    public static String generateTokenWithWrongSignature(UUID userId, String email) {
        String wrongSecret = "wrong-secret-key-FOR-TESTING-ONLY-1234567890123456789012345678901234567890123456789012345678901234567890";
        SecretKey key = Keys.hmacShaKeyFor(wrongSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + TEST_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    /**
     * Generate a malformed token (not a valid JWT structure).
     */
    public static String generateMalformedToken() {
        return "not.a.valid.jwt.token";
    }
}
