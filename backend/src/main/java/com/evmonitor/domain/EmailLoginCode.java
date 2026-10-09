package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One-time login code sent by mail (gate on the model page). Only the hash is stored.
 * A new request replaces the previous code of the address; a code dies after
 * MAX_ATTEMPTS wrong guesses or TTL.
 */
public record EmailLoginCode(UUID id, String email, String codeHash, LocalDateTime expiresAt, int attempts,
                             LocalDateTime createdAt) {

    public static final int TTL_MINUTES = 10;
    public static final int MAX_ATTEMPTS = 5;

    public static EmailLoginCode create(String email, String codeHash) {
        LocalDateTime now = LocalDateTime.now();
        return new EmailLoginCode(UUID.randomUUID(), email, codeHash, now.plusMinutes(TTL_MINUTES), 0, now);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public EmailLoginCode withFailedAttempt() {
        return new EmailLoginCode(id, email, codeHash, expiresAt, attempts + 1, createdAt);
    }

    public boolean isExhausted() {
        return attempts >= MAX_ATTEMPTS;
    }
}
