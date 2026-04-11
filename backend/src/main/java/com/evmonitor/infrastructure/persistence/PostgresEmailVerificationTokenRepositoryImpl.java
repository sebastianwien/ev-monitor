package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.EmailVerificationToken;
import com.evmonitor.domain.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresEmailVerificationTokenRepositoryImpl implements EmailVerificationTokenRepository {

    private final JpaEmailVerificationTokenRepository jpa;

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        EmailVerificationTokenEntity saved = jpa.save(toEntity(token));
        return toDomain(saved);
    }

    @Override
    public Optional<EmailVerificationToken> findByToken(String token) {
        return jpa.findByToken(token).map(this::toDomain);
    }

    @Override
    public Optional<EmailVerificationToken> findMostRecentByUserId(UUID userId) {
        return jpa.findMostRecentByUserId(userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpa.deleteByUserId(userId);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    private EmailVerificationTokenEntity toEntity(EmailVerificationToken domain) {
        return new EmailVerificationTokenEntity(
                domain.getId(),
                domain.getUserId(),
                domain.getToken(),
                domain.getExpiresAt(),
                domain.getCreatedAt());
    }

    private EmailVerificationToken toDomain(EmailVerificationTokenEntity entity) {
        return new EmailVerificationToken(
                entity.getId(),
                entity.getUserId(),
                entity.getToken(),
                entity.getExpiresAt(),
                entity.getCreatedAt());
    }
}
