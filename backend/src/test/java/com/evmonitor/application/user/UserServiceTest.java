package com.evmonitor.application.user;

import com.evmonitor.domain.*;
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

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        testUser = User.builder()
                .id(userId)
                .email("test@example.com").username("testuser").passwordHash("hashedPassword")
                .authProvider(AuthProvider.LOCAL).role("USER")
                .emailVerified(true).emailNotificationsEnabled(true)
                .referralCode("TESTCODETESTCO")
                .createdAt(now).updatedAt(now)
                .build();
    }

    @Test
    void getUserStats_shouldReturnUserStats() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(evLogRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(userRepository.isLeaderboardVisible(userId)).thenReturn(true);

        UserStatsResponse stats = userService.getUserStats(userId);

        assertNotNull(stats);
        assertEquals(testUser.getCreatedAt(), stats.registeredSince());
        assertEquals(0, stats.totalLogs());
        assertTrue(stats.leaderboardVisible());
        verify(userRepository).findById(userId);
        verify(evLogRepository).findAllByUserId(userId);
        verify(userRepository).isLeaderboardVisible(userId);
    }

    @Test
    void getUserStats_shouldNotThrowWhenKwhChargedIsNull() {
        var logWithNullKwh = EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(null)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(evLogRepository.findAllByUserId(userId)).thenReturn(List.of(logWithNullKwh));
        when(userRepository.isLeaderboardVisible(userId)).thenReturn(false);

        UserStatsResponse stats = userService.getUserStats(userId);

        assertEquals(1, stats.totalLogs());
        assertEquals(0.0, stats.totalKwh());
    }

    @Test
    void getUserStats_shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(com.evmonitor.domain.exception.NotFoundException.class, () -> userService.getUserStats(userId));
    }

    @Test
    void changeEmail_shouldCallUpdateEmail() {
        ChangeEmailRequest request = new ChangeEmailRequest("newemail@example.com", "correctPassword");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
        when(userRepository.existsByEmail(request.newEmail())).thenReturn(false);

        userService.changeEmail(userId, request);

        verify(userRepository).updateEmail(userId, "newemail@example.com");
    }

    @Test
    void changeEmail_shouldThrowExceptionWhenPasswordIsWrong() {
        ChangeEmailRequest request = new ChangeEmailRequest("newemail@example.com", "wrongPassword");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        com.evmonitor.domain.exception.ValidationException exception = assertThrows(
                com.evmonitor.domain.exception.ValidationException.class,
                () -> userService.changeEmail(userId, request)
        );
        assertEquals("WRONG_PASSWORD", exception.getCode());
        verify(userRepository, never()).updateEmail(any(), any());
    }

    @Test
    void changeEmail_shouldThrowExceptionWhenEmailAlreadyExists() {
        ChangeEmailRequest request = new ChangeEmailRequest("existing@example.com", "correctPassword");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
        when(userRepository.existsByEmail(request.newEmail())).thenReturn(true);

        com.evmonitor.domain.exception.ConflictException exception = assertThrows(
                com.evmonitor.domain.exception.ConflictException.class,
                () -> userService.changeEmail(userId, request)
        );
        assertEquals("EMAIL_TAKEN", exception.getCode());
        verify(userRepository, never()).updateEmail(any(), any());
    }

    @Test
    void changeUsername_shouldCallUpdateUsername() {
        ChangeUsernameRequest request = new ChangeUsernameRequest("newusername");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername(request.newUsername())).thenReturn(false);

        userService.changeUsername(userId, request);

        verify(userRepository).updateUsername(userId, "newusername");
    }

    @Test
    void changeUsername_shouldThrowExceptionWhenUsernameAlreadyExists() {
        ChangeUsernameRequest request = new ChangeUsernameRequest("existinguser");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername(request.newUsername())).thenReturn(true);

        com.evmonitor.domain.exception.ConflictException exception = assertThrows(
                com.evmonitor.domain.exception.ConflictException.class,
                () -> userService.changeUsername(userId, request)
        );
        assertEquals("USERNAME_TAKEN", exception.getCode());
        verify(userRepository, never()).updateUsername(any(), any());
    }

    @Test
    void changePassword_shouldCallUpdatePassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword", "newPassword123");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword", "hashedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword123")).thenReturn("newHashedPassword");

        userService.changePassword(userId, request);

        verify(userRepository).updatePassword(userId, "newHashedPassword");
    }

    @Test
    void changePassword_shouldThrowExceptionWhenCurrentPasswordIsWrong() {
        ChangePasswordRequest request = new ChangePasswordRequest("wrongPassword", "newPassword123");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        com.evmonitor.domain.exception.ValidationException exception = assertThrows(
                com.evmonitor.domain.exception.ValidationException.class,
                () -> userService.changePassword(userId, request)
        );
        assertEquals("WRONG_PASSWORD", exception.getCode());
        verify(userRepository, never()).updatePassword(any(), any());
    }

    @Test
    void exportUserData_shouldReturnJsonBytes() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(carRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(evLogRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(objectMapper.writeValueAsBytes(any())).thenReturn("{}".getBytes());

        byte[] data = userService.exportUserData(userId);

        assertNotNull(data);
        verify(userRepository).findById(userId);
        verify(objectMapper).writeValueAsBytes(any());
    }

    @Test
    void deleteAccount_shouldDeleteUserWhenPasswordIsCorrect() {
        DeleteAccountRequest request = new DeleteAccountRequest("correctPassword");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

        userService.deleteAccount(userId, request);

        verify(userRepository).delete(testUser);
    }

    @Test
    void deleteAccount_shouldThrowExceptionWhenPasswordIsWrong() {
        DeleteAccountRequest request = new DeleteAccountRequest("wrongPassword");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        com.evmonitor.domain.exception.ValidationException exception = assertThrows(
                com.evmonitor.domain.exception.ValidationException.class,
                () -> userService.deleteAccount(userId, request)
        );
        assertEquals("WRONG_PASSWORD", exception.getCode());
        verify(userRepository, never()).delete(any());
    }
}
