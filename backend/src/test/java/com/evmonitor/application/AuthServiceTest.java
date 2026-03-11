package com.evmonitor.application;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.EmailVerificationToken;
import com.evmonitor.domain.EmailVerificationTokenRepository;
import com.evmonitor.domain.PasswordResetTokenRepository;
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
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private CoinLogService coinLogService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, jwtService,
                authenticationManager, tokenRepository, passwordResetTokenRepository,
                emailService, coinLogService);
    }

    @Test
    void shouldRegisterNewUser_andSendVerificationEmail() {
        // Given
        String email = "newuser@example.com";
        String password = "SecurePassword123";
        String hashedPassword = "$2a$10$hashedPasswordExample";

        RegisterRequest request = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), password, null, null, null, null);

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
        RegisterRequest request = new RegisterRequest(email, "testuser_" + System.currentTimeMillis(), "Password123", null, null, null, null);

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
                AuthProvider.LOCAL, "USER", true /* emailVerified */, false, true, false,
                "TESTCODE", null, null, null, null, null, LocalDateTime.now(), LocalDateTime.now());

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
                AuthProvider.LOCAL, "USER", false /* emailVerified */, false, true, false,
                "TESTCODE", null, null, null, null, null, LocalDateTime.now(), LocalDateTime.now());

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
                AuthProvider.LOCAL, "USER", true, false, true, false,
                "TESTCODE", null, null, null, null, null, LocalDateTime.now(), LocalDateTime.now());

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
                "testuser_" + System.currentTimeMillis(), plainPassword, null, null, null, null);

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
                "testuser_" + System.currentTimeMillis(), "Password123", null, null, null, null);

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

    @Test
    void shouldCaptureCampaignTrackingDataOnRegistration() {
        // Given
        String email = "campaign@example.com";
        String password = "Password123";
        String utmSource = "reddit";
        String utmMedium = "cpc";
        String utmCampaign = "launch_march_2026";

        RegisterRequest request = new RegisterRequest(
                email,
                "campaign_user_" + System.currentTimeMillis(),
                password,
                null, // no referralCode
                utmSource,
                utmMedium,
                utmCampaign
        );

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

        assertEquals(utmSource, savedUser.getUtmSource());
        assertEquals(utmMedium, savedUser.getUtmMedium());
        assertEquals(utmCampaign, savedUser.getUtmCampaign());
    }

    @Test
    void shouldAllowNullCampaignData() {
        // Given - User registers without UTM parameters (organic)
        RegisterRequest request = new RegisterRequest(
                "organic@example.com",
                "organic_user_" + System.currentTimeMillis(),
                "Password123",
                null,
                null, // no utm_source
                null, // no utm_medium
                null  // no utm_campaign
        );

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

        assertNull(savedUser.getUtmSource());
        assertNull(savedUser.getUtmMedium());
        assertNull(savedUser.getUtmCampaign());
    }

    // --- resolveUsername tests ---

    @Test
    void shouldAutoGenerateUsername_fromEmailPrefix_whenNotProvided() {
        RegisterRequest request = new RegisterRequest("max@example.com", null, "Password123", null, null, null, null);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername("max")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("max", captor.getValue().getUsername());
    }

    @Test
    void shouldAutoGenerateUsername_withNumericSuffix_whenPrefixAlreadyTaken() {
        RegisterRequest request = new RegisterRequest("max@example.com", null, "Password123", null, null, null, null);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername("max")).thenReturn(true);
        when(userRepository.existsByUsername("max1")).thenReturn(true);
        when(userRepository.existsByUsername("max2")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("max2", captor.getValue().getUsername());
    }

    @Test
    void shouldSanitizeEmailPrefix_replacingSpecialCharsWithUnderscore() {
        RegisterRequest request = new RegisterRequest("test.user+extra@example.com", null, "Password123", null, null, null, null);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // dots and plus signs replaced with underscores
        assertTrue(captor.getValue().getUsername().matches("^[a-zA-Z0-9_]+$"),
                "Username should only contain alphanumeric chars and underscores");
    }

    @Test
    void shouldUseExplicitUsername_whenProvidedAndAvailable() {
        RegisterRequest request = new RegisterRequest("user@example.com", "mywantedname", "Password123", null, null, null, null);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername("mywantedname")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("mywantedname", captor.getValue().getUsername());
    }

    @Test
    void shouldRejectExplicitUsername_whenAlreadyTaken() {
        RegisterRequest request = new RegisterRequest("user@example.com", "takenname", "Password123", null, null, null, null);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername("takenname")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));
        assertEquals("Username is already taken.", ex.getMessage());

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldCombineReferralCodeAndCampaignTracking() {
        // Given - User comes via referral AND campaign (e.g. referred friend clicks Reddit ad)
        String referralCode = "ABC12345";
        UUID referrerId = UUID.randomUUID();

        RegisterRequest request = new RegisterRequest(
                "referred_campaign@example.com",
                "test_user_" + System.currentTimeMillis(),
                "Password123",
                referralCode,
                "reddit",
                "cpc",
                "referral_boost_2026"
        );

        User referrer = new User(referrerId, "referrer@example.com", "referrer", "$2a$10$hash",
                AuthProvider.LOCAL, "USER", true, false, true, false,
                referralCode, null, null, null, null, null, LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.findByReferralCode(referralCode.toUpperCase())).thenReturn(Optional.of(referrer));
        when(userRepository.countVerifiedReferrals(referrerId)).thenReturn(5L); // below MAX_REFERRALS
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        // Both referral AND campaign tracking should be captured
        assertEquals(referrerId, savedUser.getReferredByUserId());
        assertEquals("reddit", savedUser.getUtmSource());
        assertEquals("cpc", savedUser.getUtmMedium());
        assertEquals("referral_boost_2026", savedUser.getUtmCampaign());
    }
}
