package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository {
    ApiKey save(ApiKey apiKey);

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);

    List<ApiKey> findAllByUserId(UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);

    void updateLastUsedAt(UUID id);

    long countByUserId(UUID userId);
}
