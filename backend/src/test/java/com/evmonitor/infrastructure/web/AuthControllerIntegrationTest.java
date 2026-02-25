package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AuthResponse;
import com.evmonitor.application.LoginRequest;
import com.evmonitor.application.RegisterRequest;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for AuthController.
 * Tests full authentication flow with real Spring Boot context and H2 database.
 *
 * SECURITY CRITICAL: Full auth flow must work correctly!
 */
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldRegisterAndLoginNewUser() {
        // Given: New user registration with unique email
        String email = "newuser-" + System.currentTimeMillis() + "@example.com";
        String password = "SecurePassword123";
        RegisterRequest registerRequest = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), password);

        // When: Register user
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                registerRequest,
                AuthResponse.class
        );

        // Then: Registration successful
        assertEquals(HttpStatus.OK, registerResponse.getStatusCode());
        assertNotNull(registerResponse.getBody());
        assertEquals(email, registerResponse.getBody().email());
        assertNotNull(registerResponse.getBody().token());
        assertNotNull(registerResponse.getBody().userId());

        String jwtToken = registerResponse.getBody().token();

        // When: Login with same credentials
        LoginRequest loginRequest = new LoginRequest(email, password);
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login",
                loginRequest,
                AuthResponse.class
        );

        // Then: Login successful
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        assertNotNull(loginResponse.getBody());
        assertEquals(email, loginResponse.getBody().email());
        assertNotNull(loginResponse.getBody().token());

        // Verify user in database
        User savedUser = userRepository.findByEmail(email).orElseThrow();
        assertEquals(email, savedUser.getEmail());
        assertNotNull(savedUser.getPasswordHash());
        assertNotEquals(password, savedUser.getPasswordHash()); // Password should be hashed
    }

    @Test
    void shouldRejectDuplicateRegistration() {
        // Given: First registration succeeds
        String email = "duplicate-test-" + System.currentTimeMillis() + "@example.com";
        RegisterRequest firstRequest = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), "AnyPassword123");

        ResponseEntity<AuthResponse> firstResponse = restTemplate.postForEntity(
                "/api/auth/register",
                firstRequest,
                AuthResponse.class
        );
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());

        // When: Try to register again with same email
        RegisterRequest duplicateRequest = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), "DifferentPassword456");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register",
                duplicateRequest,
                String.class
        );

        // Then: Should be rejected (500 with IllegalArgumentException)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void shouldRejectLoginWithInvalidCredentials() {
        // Given: User registered with correct password
        String email = "logintest-" + System.currentTimeMillis() + "@example.com";
        String correctPassword = "CorrectPassword123";

        RegisterRequest registerRequest = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), correctPassword);
        restTemplate.postForEntity("/api/auth/register", registerRequest, AuthResponse.class);

        // When: Try to login with wrong password
        LoginRequest wrongPasswordRequest = new LoginRequest(email, "WrongPassword456");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                wrongPasswordRequest,
                String.class
        );

        // Then: Should be rejected (401 Unauthorized or 403 Forbidden)
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
    }

    @Test
    void shouldRejectLoginWithNonExistentEmail() {
        // Given: No user with this email
        String nonExistentEmail = "nonexistent@example.com";
        LoginRequest request = new LoginRequest(nonExistentEmail, "AnyPassword123");

        // When: Try to login
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                request,
                String.class
        );

        // Then: Should be rejected (500 because AuthService throws IllegalArgumentException)
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN ||
                response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR,
                "Expected 401, 403 or 500, got: " + response.getStatusCode()
        );
    }

    @Test
    void shouldAccessProtectedEndpoint_WithValidToken() {
        // Given: User registered and has JWT token
        String email = "authenticated-" + System.currentTimeMillis() + "@example.com";
        String password = "Password123";
        RegisterRequest registerRequest = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), password);

        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                registerRequest,
                AuthResponse.class
        );

        String jwtToken = registerResponse.getBody().token();
        java.util.UUID userId = registerResponse.getBody().userId();

        // When: Access protected endpoint with valid token
        HttpEntity<Void> requestWithAuth = createAuthRequest(userId, email);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars",
                HttpMethod.GET,
                requestWithAuth,
                String.class
        );

        // Then: Access granted
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldRejectProtectedEndpoint_WithoutToken() {
        // When: Try to access protected endpoint without token
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/cars",
                String.class
        );

        // Then: Access denied (401 or 403)
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode()
        );
    }

    @Test
    void shouldRejectProtectedEndpoint_WithInvalidToken() {
        // Given: Invalid JWT token
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer invalid.jwt.token");
        HttpEntity<Void> requestWithInvalidAuth = new HttpEntity<>(headers);

        // When: Try to access protected endpoint
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars",
                HttpMethod.GET,
                requestWithInvalidAuth,
                String.class
        );

        // Then: Access denied (500 because JWT parsing fails with exception)
        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN ||
                response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR,
                "Expected 401/403/500, got: " + response.getStatusCode()
        );
    }
}
