package com.evmonitor.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailLoginCodeRepository {
    EmailLoginCode save(EmailLoginCode code);
    Optional<EmailLoginCode> findLatestByEmail(String email);
    void deleteByEmail(String email);
    void deleteExpired(LocalDateTime now);
}
