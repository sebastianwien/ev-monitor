package com.evmonitor.domain;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository {
    EmailVerificationToken save(EmailVerificationToken token);
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findMostRecentByUserId(UUID userId);
    void deleteByUserId(UUID userId);
    void deleteById(UUID id);
}
