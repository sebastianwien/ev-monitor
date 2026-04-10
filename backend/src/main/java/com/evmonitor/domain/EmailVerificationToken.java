package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class EmailVerificationToken {

    private final UUID id;
    private final UUID userId;
    private final String token;
    private final LocalDateTime expiresAt;
    private final LocalDateTime createdAt;

    public static EmailVerificationToken createFor(UUID userId) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32]; // 256 bits entropy
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();
        return new EmailVerificationToken(UUID.randomUUID(), userId, token, now.plusHours(24), now);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
