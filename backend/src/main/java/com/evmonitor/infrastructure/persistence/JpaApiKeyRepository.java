package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    Optional<ApiKeyEntity> findByIdAndUserId(UUID id, UUID userId);

    List<ApiKeyEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Modifying
    @Query("UPDATE ApiKeyEntity a SET a.lastUsedAt = :now WHERE a.id = :id")
    void updateLastUsedAt(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
