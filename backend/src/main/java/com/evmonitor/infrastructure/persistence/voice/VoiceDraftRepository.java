package com.evmonitor.infrastructure.persistence.voice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface VoiceDraftRepository extends JpaRepository<VoiceDraftEntity, UUID> {

    /** Erfolgreiche Aufnahmen seit Monatsbeginn - Fehlschlaege zaehlen nicht gegen den Deckel. */
    @Query("SELECT COUNT(v) FROM VoiceDraftEntity v WHERE v.userId = :userId AND v.success = true AND v.createdAt >= :since")
    long countSuccessfulSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    /** Beginn der Kennenlernphase. */
    @Query("SELECT MIN(v.createdAt) FROM VoiceDraftEntity v WHERE v.userId = :userId AND v.success = true")
    Optional<LocalDateTime> findFirstSuccessAt(@Param("userId") UUID userId);

    /** Kumulierte Mistral-Kosten aller Aufnahmen (auch Fehlschlaege, die kosten ebenfalls). */
    @Query("SELECT COALESCE(SUM(v.costUsd), 0) FROM VoiceDraftEntity v")
    BigDecimal sumCostUsd();

    @Query("SELECT COALESCE(SUM(v.costUsd), 0) FROM VoiceDraftEntity v WHERE v.createdAt >= :since")
    BigDecimal sumCostUsdSince(@Param("since") LocalDateTime since);

    /** Kontoloeschung (zusaetzlich zum FK-CASCADE, damit auch ohne Flyway-Schema testbar). */
    @Modifying
    @Transactional
    @Query("DELETE FROM VoiceDraftEntity v WHERE v.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
