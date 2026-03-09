package com.evmonitor.application.user;

import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JpaUserRepository jpaUserRepository;
    private final EvLogRepository evLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Get stats from EvLogs
        var logs = evLogRepository.findAllByUserId(userId);
        int totalLogs = logs.size();
        double totalKwh = logs.stream().mapToDouble(log -> log.getKwhCharged().doubleValue()).sum();
        double totalCostEur = logs.stream()
                .filter(log -> log.getCostEur() != null)
                .mapToDouble(log -> log.getCostEur().doubleValue())
                .sum();

        return new UserStatsResponse(
                user.getCreatedAt(),
                totalLogs,
                totalKwh,
                totalCostEur,
                user.getReferralCode()
        );
    }

    @Transactional
    public void changeEmail(UUID userId, ChangeEmailRequest request) {
        UserEntity userEntity = jpaUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if email is already taken
        if (userRepository.existsByEmail(request.newEmail())) {
            throw new IllegalArgumentException("Email bereits vergeben");
        }

        userEntity.setEmail(request.newEmail());
        userEntity.setEmailVerified(false); // Re-verification required!
        userEntity.setUpdatedAt(LocalDateTime.now());

        jpaUserRepository.save(userEntity);

        // TODO: Send verification email (implement EmailVerificationService call)
    }

    @Transactional
    public void changeUsername(UUID userId, ChangeUsernameRequest request) {
        UserEntity userEntity = jpaUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if username is already taken
        if (userRepository.existsByUsername(request.newUsername())) {
            throw new IllegalArgumentException("Username bereits vergeben");
        }

        userEntity.setUsername(request.newUsername());
        userEntity.setUpdatedAt(LocalDateTime.now());

        jpaUserRepository.save(userEntity);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        UserEntity userEntity = jpaUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.currentPassword(), userEntity.getPasswordHash())) {
            throw new IllegalArgumentException("Aktuelles Passwort ist falsch");
        }

        // Set new password
        userEntity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userEntity.setUpdatedAt(LocalDateTime.now());

        jpaUserRepository.save(userEntity);
    }

    @Transactional(readOnly = true)
    public byte[] exportUserData(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            Map<String, Object> exportData = new HashMap<>();

            // User data (exclude password hash!)
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId().toString());
            userData.put("email", user.getEmail());
            userData.put("username", user.getUsername());
            userData.put("emailVerified", user.isEmailVerified());
            userData.put("createdAt", user.getCreatedAt());
            userData.put("updatedAt", user.getUpdatedAt());
            exportData.put("user", userData);

            // EvLogs
            var logs = evLogRepository.findAllByUserId(userId);
            exportData.put("evLogs", logs);

            // TODO: Add Cars, CoinLogs and VehicleSpecifications

            return objectMapper.writeValueAsBytes(exportData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export user data", e);
        }
    }

    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Passwort ist falsch");
        }

        // Delete user (CASCADE will delete all related data: Cars, EvLogs, CoinLogs, Tokens)
        userRepository.delete(user);
    }
}
