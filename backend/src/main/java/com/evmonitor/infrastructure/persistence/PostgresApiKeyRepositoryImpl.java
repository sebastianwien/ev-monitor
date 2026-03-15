package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.ApiKey;
import com.evmonitor.domain.ApiKeyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresApiKeyRepositoryImpl implements ApiKeyRepository {

    private final JpaApiKeyRepository jpaRepository;

    public PostgresApiKeyRepositoryImpl(JpaApiKeyRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        ApiKeyEntity saved = jpaRepository.save(toEntity(apiKey));
        return toDomain(saved);
    }

    @Override
    public Optional<ApiKey> findByKeyHash(String keyHash) {
        return jpaRepository.findByKeyHash(keyHash).map(this::toDomain);
    }

    @Override
    public Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public List<ApiKey> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countByUserId(UUID userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        jpaRepository.deleteByIdAndUserId(id, userId);
    }

    @Override
    @Transactional
    public void updateLastUsedAt(UUID id) {
        jpaRepository.updateLastUsedAt(id, LocalDateTime.now());
    }

    private ApiKeyEntity toEntity(ApiKey domain) {
        return new ApiKeyEntity(
                domain.getId(),
                domain.getUserId(),
                domain.getKeyHash(),
                domain.getKeyPrefix(),
                domain.getName(),
                domain.getLastUsedAt(),
                domain.getCreatedAt()
        );
    }

    private ApiKey toDomain(ApiKeyEntity entity) {
        return new ApiKey(
                entity.getId(),
                entity.getUserId(),
                entity.getKeyHash(),
                entity.getKeyPrefix(),
                entity.getName(),
                entity.getLastUsedAt(),
                entity.getCreatedAt()
        );
    }
}
