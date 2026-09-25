package com.evmonitor.infrastructure.persistence.ingest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ImportEventRepository extends JpaRepository<ImportEvent, UUID> {

    /** Kontolöschung (zusätzlich zum FK-CASCADE, damit auch ohne Flyway-Schema testbar). */
    @Modifying
    @Transactional
    @Query("DELETE FROM ImportEvent e WHERE e.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /** TTL-Purge. */
    @Modifying
    @Transactional
    @Query("DELETE FROM ImportEvent e WHERE e.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
