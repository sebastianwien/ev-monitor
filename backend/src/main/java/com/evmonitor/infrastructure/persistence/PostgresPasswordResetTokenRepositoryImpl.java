package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.PasswordResetToken;
import com.evmonitor.domain.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresPasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final JpaPasswordResetTokenRepository jpa;

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return toDomain(jpa.save(toEntity(token)));
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return jpa.findByToken(token).map(this::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpa.deleteByUserId(userId);
    }

    private PasswordResetTokenEntity toEntity(PasswordResetToken domain) {
        return new PasswordResetTokenEntity(
                domain.getId(), domain.getUserId(), domain.getToken(),
                domain.getExpiresAt(), domain.getCreatedAt());
    }

    private PasswordResetToken toDomain(PasswordResetTokenEntity entity) {
        return new PasswordResetToken(
                entity.getId(), entity.getUserId(), entity.getToken(),
                entity.getExpiresAt(), entity.getCreatedAt());
    }
}
