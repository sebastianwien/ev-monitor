package com.evmonitor.application;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.EmailVerificationToken;
import com.evmonitor.domain.EmailVerificationTokenRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for AuthService.
 * Tests user registration, login, and email verification flow.
 *
 * SECURITY CRITICAL: Authentication is the first line of defense.
 * Any bug here = unauthorized access!
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private CoinLogService coinLogService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, jwtService,
                authenticationManager, tokenRepository, emailService, coinLogService);
    }

    @Test
    void shouldRegisterNewUser_andSendVerificationEmail() {
        // Given
        String email = "newuser@example.com";
        String password = "SecurePassword123";
        String hashedPassword = "$2a$10$hashedPasswordExample";

        RegisterRequest request = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), password, null);

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(hashedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        RegisterResponse response = authService.register(request);

        // Then
        assertNotNull(response);
        assertEquals("PENDING_VERIFICATION", response.status());
        assertEquals(email, response.email());

        // Verification email must be sent
        verify(emailService).sendVerificationEmail(eq(email), anyString());
        verify(tokenRepository).save(any(EmailVerificationToken.class));

        // No JWT should be issued yet
        verify(jwtService, never()).generateToken(any(UserPrincipal.class));
    }

    @Test
    void shouldRejectRegistrationWithDuplicateEmail() {
        // Given
        String email = "existing@example.com";
        RegisterRequest request = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), "Password123", null);

        when(userRepository.existsByEmail(email)).thenReturn(true);

        // When & Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));
        assertEquals("Email is already in use.", ex.getMessage());

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void shouldLoginWithValidVerifiedCredentials() {
        // Given
        String email = "user@example.com";
        String password = "CorrectPassword";
        String jwtToken = "jwt.token.here";
        UUID userId = UUID.randomUUID();

        User user = new User(userId, email, "testuser", "$2a$10$hashedPassword",
                AuthProvider.LOCAL, "USER", true /* emailVerified */, false, true,
                "TESTCODE", null, LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn(jwtToken);

        // When
        AuthResponse response = authService.login(new LoginRequest(email, password));

        // Then
        assertNotNull(response);
        assertEquals(email, response.email());
        assertEquals(jwtToken, response.token());
        assertEquals("USER", response.role());
    }

    @Test
    void shouldRejectLoginForUnverifiedEmail() {
        // Given
        String email = "unverified@example.com";
        UUID userId = UUID.randomUUID();

        User unverifiedUser = new User(userId, email, "unverified", "$2a$10$hash",
                AuthProvider.LOCAL, "USER", false /* emailVerified */, false, true,
                "TESTCODE", null, LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(unverifiedUser));
        when(authenticationManager.authenticate(any())).thenReturn(null);

        // When & Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login(new LoginRequest(email, "Password123")));
        assertEquals("EMAIL_NOT_VERIFIED", ex.getMessage());

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void shouldVerifyEmailAndReturnJwt() {
        // Given
        UUID userId = UUID.randomUUID();
        String rawToken = "secure-random-token";
        String jwtToken = "jwt.token.here";

        EmailVerificationToken verificationToken = new EmailVerificationToken(
                UUID.randomUUID(), userId, rawToken, LocalDateTime.now().plusHours(24), LocalDateTime.now());

        User user = new User(userId, "user@example.com", "user", "$2a$10$hash",
                AuthProvider.LOCAL, "USER", true, false, true,
                "TESTCODE", null, LocalDateTime.now(), LocalDateTime.now());

        when(tokenRepository.findByToken(rawToken)).thenReturn(Optional.of(verificationToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn(jwtToken);

        // When
        AuthResponse response = authService.verifyEmail(rawToken);

        // Then
        assertNotNull(response);
        assertEquals(jwtToken, response.token());
        verify(userRepository).markEmailVerified(userId);
        verify(tokenRepository).deleteById(verificationToken.getId());
    }

    @Test
    void shouldRejectExpiredVerificationToken() {
        // Given
        String rawToken = "expired-token";
        EmailVerificationToken expiredToken = new EmailVerificationToken(
                UUID.randomUUID(), UUID.randomUUID(), rawToken,
                LocalDateTime.now().minusHours(1), // already expired!
                LocalDateTime.now().minusHours(25));

        when(tokenRepository.findByToken(rawToken)).thenReturn(Optional.of(expiredToken));

        // When & Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.verifyEmail(rawToken));
        assertEquals("TOKEN_EXPIRED", ex.getMessage());

        verify(userRepository, never()).markEmailVerified(any());
    }

    @Test
    void shouldHashPasswordBeforeSaving() {
        // Given
        String plainPassword = "PlainTextPassword123";
        String expectedHash = "$2a$10$hashedPasswordExample";
        RegisterRequest request = new RegisterRequest("user@example.com",
                "testuser_" + System.currentTimeMillis(), plainPassword, null);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoder.encode(plainPassword)).thenReturn(expectedHash);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertNotEquals(plainPassword, savedUser.getPasswordHash());
        assertEquals(expectedHash, savedUser.getPasswordHash());
    }

    @Test
    void shouldCreateUnverifiedLocalUserOnRegistration() {
        // Given
        RegisterRequest request = new RegisterRequest("user@example.com",
                "testuser_" + System.currentTimeMillis(), "Password123", null);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals(AuthProvider.LOCAL, savedUser.getAuthProvider());
        assertEquals("USER", savedUser.getRole());
        assertFalse(savedUser.isEmailVerified(), "Newly registered user should NOT be verified yet");
    }
}
