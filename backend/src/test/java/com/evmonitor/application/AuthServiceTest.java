package com.evmonitor.application;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for AuthService.
 * Tests user registration, login, and password hashing.
 *
 * SECURITY CRITICAL: Authentication is the first line of defense.
 * Any bug here = unauthorized access!
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, authenticationManager);
    }

    @Test
    void shouldRegisterNewUser() {
        // Given
        String email = "newuser@example.com";
        String password = "SecurePassword123";
        String hashedPassword = "$2a$10$hashedPasswordExample";
        String jwtToken = "jwt.token.here";

        RegisterRequest request = new RegisterRequest(email, password);

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(hashedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn(jwtToken);

        // When
        AuthResponse response = authService.register(request);

        // Then
        assertNotNull(response);
        assertEquals(email, response.email());
        assertEquals(jwtToken, response.token());
        assertEquals("USER", response.role());

        // Verify password was hashed
        verify(passwordEncoder).encode(password);

        // Verify user was saved
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(email, savedUser.getEmail());
        assertEquals(hashedPassword, savedUser.getPasswordHash());
    }

    @Test
    void shouldRejectRegistrationWithDuplicateEmail() {
        // Given
        String email = "existing@example.com";
        RegisterRequest request = new RegisterRequest(email, "Password123");

        when(userRepository.existsByEmail(email)).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            authService.register(request);
        });

        assertEquals("Email is already in use.", exception.getMessage());

        // Verify no user was saved
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldLoginWithValidCredentials() {
        // Given
        String email = "user@example.com";
        String password = "CorrectPassword";
        String jwtToken = "jwt.token.here";

        UUID userId = UUID.randomUUID();
        User user = new User(
                userId,
                email,
                "$2a$10$hashedPassword",
                AuthProvider.LOCAL,
                "USER",
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        );

        LoginRequest request = new LoginRequest(email, password);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null); // Successful authentication
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn(jwtToken);

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertNotNull(response);
        assertEquals(email, response.email());
        assertEquals(userId, response.userId());
        assertEquals(jwtToken, response.token());
        assertEquals("USER", response.role());

        // Verify authentication was attempted
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldRejectLoginWithInvalidCredentials() {
        // Given
        String email = "user@example.com";
        String password = "WrongPassword";
        LoginRequest request = new LoginRequest(email, password);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // When & Then
        assertThrows(BadCredentialsException.class, () -> {
            authService.login(request);
        });

        // Verify no JWT was generated
        verify(jwtService, never()).generateToken(any(UserPrincipal.class));
    }

    @Test
    void shouldHashPasswordBeforeSaving() {
        // Given
        String plainPassword = "PlainTextPassword123";
        String expectedHash = "$2a$10$hashedPasswordExample";
        RegisterRequest request = new RegisterRequest("user@example.com", plainPassword);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(plainPassword)).thenReturn(expectedHash);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt.token");

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        // Password should be hashed, not plain text
        assertNotEquals(plainPassword, savedUser.getPasswordHash());
        assertEquals(expectedHash, savedUser.getPasswordHash());
    }

    @Test
    void shouldCreateLocalUserOnRegistration() {
        // Given
        RegisterRequest request = new RegisterRequest("user@example.com", "Password123");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt.token");

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        // Should be a local user (no OAuth)
        assertEquals(AuthProvider.LOCAL, savedUser.getAuthProvider());
        assertEquals("USER", savedUser.getRole());
    }
}
