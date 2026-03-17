package com.evmonitor.application.user;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JpaUserRepository jpaUserRepository;

    @Mock
    private EvLogRepository evLogRepository;

    @Mock
    private CarRepository carRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User testUser;
    private UserEntity testUserEntity;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User(
                userId,
                "test@example.com",
                "testuser",
                "hashedPassword",
                AuthProvider.LOCAL,
                "USER",
                true,
                false,
                true,
                false, // premium
                "TESTCODE",
                null,
                null, // stripeCustomerId
                null, // utmSource
                null, // utmMedium
                null, // utmCampaign
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        testUserEntity = new UserEntity(
                userId,
                "test@example.com",
                "testuser",
                "hashedPassword",
                AuthProvider.LOCAL,
                "USER",
                true,
                false,
                true,
                "TESTCODE",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void getUserStats_shouldReturnUserStats() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(evLogRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(jpaUserRepository.findById(userId)).thenReturn(Optional.of(testUserEntity));

        // When
        UserStatsResponse stats = userService.getUserStats(userId);

        // Then
        assertNotNull(stats);
        assertEquals(testUser.getCreatedAt(), stats.registeredSince());
        assertEquals(0, stats.totalLogs());
        verify(userRepository).findById(userId);
        verify(evLogRepository).findAllByUserId(userId);
    }

    @Test
    void changeEmail_shouldUpdateEmailAndSetEmailVerifiedToFalse() {
        // Given
        ChangeEmailRequest request = new ChangeEmailRequest("newemail@example.com", "correctPassword");
        when(jpaUserRepository.findById(userId)).thenReturn(Optional.of(testUserEntity));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
        when(userRepository.existsByEmail(request.newEmail())).thenReturn(false);

        // When
        userService.changeEmail(userId, request);

        // Then
        assertEquals("newemail@example.com", testUserEntity.getEmail());
        assertFalse(testUserEntity.isEmailVerified());
        verify(jpaUserRepository).save(testUserEntity);
    }

    @Test
    void changeEmail_shouldThrowExceptionWhenPasswordIsWrong() {
        // Given
        ChangeEmailRequest request = new ChangeEmailRequest("newemail@example.com", "wrongPassword");
        when(jpaUserRepository.findById(userId)).thenReturn(Optional.of(testUserEntity));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.changeEmail(userId, request)
        );
        assertEquals("Aktuelles Passwort ist falsch", exception.getMessage());
        verify(jpaUserRepository, never()).save(any());
    }

    @Test
    void changeEmail_shouldThrowExceptionWhenEmailAlreadyExists() {
        // Given
        ChangeEmailRequest request = new ChangeEmailRequest("existing@example.com", "correctPassword");
        when(jpaUserRepository.findById(userId)).thenReturn(Optional.of(testUserEntity));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
        when(userRepository.existsByEmail(request.newEmail())).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.changeEmail(userId, request)
        );
        assertEquals("Email bereits vergeben", exception.getMessage());
        verify(jpaUserRepository, never()).save(any());
    }

    @Test
    void changeUsername_shouldUpdateUsername() {
        // Given
        ChangeUsernameRequest request = new ChangeUsernameRequest("newusername");
        when(jpaUserRepository.findById(userId)).thenReturn(Optional.of(testUserEntity));
        when(userRepository.existsByUsername(request.newUsername())).thenReturn(false);

        // When
        userService.changeUsername(userId, request);

        // Then
        assertEquals("newusername", testUserEntity.getUsername());
        verify(jpaUserRepository).save(testUserEntity);
    }

    @Test
    void changeUsername_shouldThrowExceptionWhenUsernameAlreadyExists() {
        // Given
        ChangeUsernameRequest request = new ChangeUsernameRequest("existinguser");
        when(jpaUserRepository.findById(userId)).thenReturn(Optional.of(testUserEntity));
        when(userRepository.existsByUsername(request.newUsername())).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.changeUsername(userId, request)
        );
        assertEquals("Username bereits vergeben", exception.getMessage());
        verify(jpaUserRepository, never()).save(any());
    }

    @Test
    void changePassword_shouldUpdatePassword() {
        // Given
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword", "newPassword123");
        when(jpaUserRepository.findById(userId)).thenReturn(Optional.of(testUserEntity));
        when(passwordEncoder.matches(request.currentPassword(), testUserEntity.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode(request.newPassword())).thenReturn("newHashedPassword");

        // When
        userService.changePassword(userId, request);

        // Then
        assertEquals("newHashedPassword", testUserEntity.getPasswordHash());
        verify(jpaUserRepository).save(testUserEntity);
    }

    @Test
    void changePassword_shouldThrowExceptionWhenCurrentPasswordIsWrong() {
        // Given
        ChangePasswordRequest request = new ChangePasswordRequest("wrongPassword", "newPassword123");
        when(jpaUserRepository.findById(userId)).thenReturn(Optional.of(testUserEntity));
        when(passwordEncoder.matches(request.currentPassword(), testUserEntity.getPasswordHash())).thenReturn(false);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.changePassword(userId, request)
        );
        assertEquals("Aktuelles Passwort ist falsch", exception.getMessage());
        verify(jpaUserRepository, never()).save(any());
    }

    @Test
    void exportUserData_shouldReturnJsonBytes() throws Exception {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(carRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(evLogRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(objectMapper.writeValueAsBytes(any())).thenReturn("{}".getBytes());

        // When
        byte[] data = userService.exportUserData(userId);

        // Then
        assertNotNull(data);
        verify(userRepository).findById(userId);
        verify(evLogRepository).findAllByUserId(userId);
        verify(objectMapper).writeValueAsBytes(any());
    }

    @Test
    void deleteAccount_shouldDeleteUserWhenPasswordIsCorrect() {
        // Given
        DeleteAccountRequest request = new DeleteAccountRequest("correctPassword");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(true);

        // When
        userService.deleteAccount(userId, request);

        // Then
        verify(userRepository).delete(testUser);
    }

    @Test
    void deleteAccount_shouldThrowExceptionWhenPasswordIsWrong() {
        // Given
        DeleteAccountRequest request = new DeleteAccountRequest("wrongPassword");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(false);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.deleteAccount(userId, request)
        );
        assertEquals("Passwort ist falsch", exception.getMessage());
        verify(userRepository, never()).delete(any());
    }

    @Test
    void getUserStats_shouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> userService.getUserStats(userId));
    }
}
