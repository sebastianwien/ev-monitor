package com.evmonitor.application.user;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> VALID_COUNTRIES = Set.of(
            "DE", "AT", "CH", "GB", "NL", "BE", "DK", "NO", "SE");

    private final UserRepository userRepository;
    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(UUID userId) {
        User user = requireUser(userId);

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
                user.getReferralCode(),
                userRepository.isLeaderboardVisible(userId)
        );
    }

    @Transactional
    public void changeEmail(UUID userId, ChangeEmailRequest request) {
        User user = requireUser(userId);

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new ValidationException("OAUTH_ACCOUNT", "Email-Änderung nur für lokal registrierte Accounts möglich");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ValidationException("WRONG_PASSWORD", "Aktuelles Passwort ist falsch");
        }

        if (userRepository.existsByEmail(request.newEmail())) {
            throw new ConflictException("EMAIL_TAKEN", "Email bereits vergeben");
        }

        userRepository.updateEmail(userId, request.newEmail());
    }

    @Transactional
    public void changeUsername(UUID userId, ChangeUsernameRequest request) {
        requireUser(userId);

        if (userRepository.existsByUsername(request.newUsername())) {
            throw new ConflictException("USERNAME_TAKEN", "Username bereits vergeben");
        }

        userRepository.updateUsername(userId, request.newUsername());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = requireUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ValidationException("WRONG_PASSWORD", "Aktuelles Passwort ist falsch");
        }

        userRepository.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public byte[] exportUserData(UUID userId) {
        User user = requireUser(userId);

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

            // Cars
            var cars = carRepository.findAllByUserId(userId);
            exportData.put("cars", cars);

            // EvLogs
            var logs = evLogRepository.findAllByUserId(userId);
            exportData.put("evLogs", logs);

            return objectMapper.writeValueAsBytes(exportData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export user data", e);
        }
    }

    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = requireUser(userId);

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ValidationException("WRONG_PASSWORD", "Passwort ist falsch");
        }

        // Delete user (CASCADE will delete all related data: Cars, EvLogs, CoinLogs, Tokens)
        userRepository.delete(user);
    }

    @Transactional
    public void setLeaderboardVisible(UUID userId, boolean visible) {
        userRepository.setLeaderboardVisible(userId, visible);
    }

    @Transactional
    public void updateCountry(UUID userId, String country) {
        if (country == null || !VALID_COUNTRIES.contains(country)) {
            throw new ValidationException("INVALID_COUNTRY", "Invalid country code: " + country);
        }
        userRepository.updateCountry(userId, country);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.forEntity("User", userId));
    }
}
