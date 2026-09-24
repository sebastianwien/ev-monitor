package com.evmonitor.infrastructure.persistence.sample;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ImportSampleRepository extends JpaRepository<ImportSample, UUID> {

    /** Dieselbe Datei kommt oft zweimal (Vorschau, dann Import) - nur einmal ablegen. */
    boolean existsByProviderAndContentSha256(String provider, String contentSha256);

    /** Kontoloeschung (zusaetzlich zum FK-CASCADE, damit auch ohne Flyway-Schema testbar). */
    @Modifying
    @Transactional
    @Query("DELETE FROM ImportSample s WHERE s.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /** TTL-Purge. */
    @Modifying
    @Transactional
    @Query("DELETE FROM ImportSample s WHERE s.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
