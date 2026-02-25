package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AuthResponse;
import com.evmonitor.application.LoginRequest;
import com.evmonitor.application.RegisterRequest;
import com.evmonitor.application.RegisterResponse;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Integration Tests for AuthController.
 * EmailService is mocked to prevent actual email sending in tests.
 *
 * SECURITY CRITICAL: Full auth flow must work correctly!
 */
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private EmailService emailService;

    private RegisterResponse registerUser(String email, String username, String password) {
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());
        RegisterRequest request = new RegisterRequest(email, username, password);
        ResponseEntity<RegisterResponse> response = restTemplate.postForEntity(
                "/api/auth/register", request, RegisterResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private AuthResponse registerAndVerify(String email, String username, String password) {
        registerUser(email, username, password);
        // Directly mark user as verified (simulates clicking the email link)
        User user = userRepository.findByEmail(email).orElseThrow();
        userRepository.markEmailVerified(user.getId());
        // Login
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, password), AuthResponse.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        return loginResponse.getBody();
    }

    @Test
    void shouldRegisterUser_andReturnPendingVerification() {
        String email = "newuser-" + System.currentTimeMillis() + "@example.com";
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());

        ResponseEntity<RegisterResponse> response = restTemplate.postForEntity(
                "/api/auth/register",
                new RegisterRequest(email, "user_" + System.currentTimeMillis(), "SecurePassword123"),
                RegisterResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PENDING_VERIFICATION", response.getBody().status());
        assertEquals(email, response.getBody().email());
    }

    @Test
    void shouldLoginAfterEmailVerification() {
        String email = "verified-" + System.currentTimeMillis() + "@example.com";
        String password = "SecurePassword123";

        AuthResponse authResponse = registerAndVerify(email, "user_" + System.currentTimeMillis(), password);

        assertNotNull(authResponse);
        assertEquals(email, authResponse.email());
        assertNotNull(authResponse.token());
        assertNotNull(authResponse.userId());
    }

    @Test
    void shouldRejectLoginForUnverifiedUser() {
        String email = "unverified-" + System.currentTimeMillis() + "@example.com";
        registerUser(email, "user_" + System.currentTimeMillis(), "Password123");

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "Password123"), String.class);

        // 403 FORBIDDEN because email not verified
        assertEquals(HttpStatus.FORBIDDEN, loginResponse.getStatusCode());
    }

    @Test
    void shouldRejectDuplicateRegistration() {
        String email = "duplicate-" + System.currentTimeMillis() + "@example.com";
        registerUser(email, "user_" + System.currentTimeMillis(), "Password123");

        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register",
                new RegisterRequest(email, "user_" + System.currentTimeMillis(), "DifferentPassword456"),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldRejectLoginWithInvalidCredentials() {
        String email = "logintest-" + System.currentTimeMillis() + "@example.com";
        registerAndVerify(email, "user_" + System.currentTimeMillis(), "CorrectPassword123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "WrongPassword456"), String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode());
    }

    @Test
    void shouldRejectLoginWithNonExistentEmail() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest("nonexistent@example.com", "AnyPassword123"),
                String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN ||
                response.getStatusCode() == HttpStatus.BAD_REQUEST ||
                response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR,
                "Expected 4xx or 500, got: " + response.getStatusCode());
    }

    @Test
    void shouldAccessProtectedEndpoint_WithValidToken() {
        String email = "authenticated-" + System.currentTimeMillis() + "@example.com";
        AuthResponse auth = registerAndVerify(email, "user_" + System.currentTimeMillis(), "Password123");

        HttpEntity<Void> requestWithAuth = createAuthRequest(auth.userId(), email);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars", HttpMethod.GET, requestWithAuth, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldRejectProtectedEndpoint_WithoutToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/cars", String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Expected 401 or 403, got: " + response.getStatusCode());
    }

    @Test
    void shouldRejectProtectedEndpoint_WithInvalidToken() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer invalid.jwt.token");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertTrue(
                response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                response.getStatusCode() == HttpStatus.FORBIDDEN ||
                response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR,
                "Expected 401/403/500, got: " + response.getStatusCode());
    }
}
