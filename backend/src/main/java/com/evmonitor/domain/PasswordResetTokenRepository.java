package com.evmonitor.domain;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByToken(String token);
    void deleteById(UUID id);
    void deleteByUserId(UUID userId);
}
