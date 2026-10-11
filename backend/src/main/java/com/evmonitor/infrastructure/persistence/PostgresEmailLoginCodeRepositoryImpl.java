package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.EmailLoginCode;
import com.evmonitor.domain.EmailLoginCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PostgresEmailLoginCodeRepositoryImpl implements EmailLoginCodeRepository {

    private final JpaEmailLoginCodeRepository jpa;

    @Override
    public EmailLoginCode save(EmailLoginCode code) {
        return toDomain(jpa.save(new EmailLoginCodeEntity(code.id(), code.email(), code.codeHash(),
                code.expiresAt(), code.attempts(), code.createdAt())));
    }

    @Override
    public Optional<EmailLoginCode> findLatestByEmail(String email) {
        return jpa.findFirstByEmailOrderByCreatedAtDesc(email).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteByEmail(String email) {
        jpa.deleteByEmail(email);
    }

    @Override
    @Transactional
    public void deleteExpired(LocalDateTime now) {
        jpa.deleteExpired(now);
    }

    private EmailLoginCode toDomain(EmailLoginCodeEntity e) {
        return new EmailLoginCode(e.getId(), e.getEmail(), e.getCodeHash(), e.getExpiresAt(), e.getAttempts(),
                e.getCreatedAt());
    }
}
