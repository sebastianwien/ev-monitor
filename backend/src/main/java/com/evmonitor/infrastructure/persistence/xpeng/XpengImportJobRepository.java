package com.evmonitor.infrastructure.persistence.xpeng;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface XpengImportJobRepository extends JpaRepository<XpengImportJob, UUID> {
    Optional<XpengImportJob> findByIdAndUserId(UUID id, UUID userId);
    List<XpengImportJob> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<XpengImportJob> findFirstByUserIdAndFileHashAndStatusIn(
            UUID userId, String fileHash, Collection<XpengImportJob.Status> statuses);
    List<XpengImportJob> findAllByStatus(XpengImportJob.Status status);

    /**
     * Claimt den aeltesten QUEUED-Job. {@code FOR UPDATE SKIP LOCKED} macht den Claim auch bei
     * mehreren Worker-Instanzen exklusiv; muss innerhalb einer Transaktion laufen.
     */
    @Query(value = "SELECT * FROM xpeng_import_job WHERE status = 'QUEUED' "
            + "ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<XpengImportJob> findNextQueuedForUpdate();

    /**
     * Haengengebliebene PROCESSING-Jobs (Container abgestuerzt) als FAILED markieren. Nur Jobs, die
     * vor {@code startedBefore} begonnen haben und nicht {@code excludeId} sind: beim Blue/Green-Deploy
     * arbeitet der alte Container noch, dessen laufende Jobs bleiben unangetastet.
     */
    @Modifying
    @Query("UPDATE XpengImportJob j SET j.status = 'FAILED', j.errorMessage = :reason, j.completedAt = :now "
            + "WHERE j.status = 'PROCESSING' AND j.startedAt < :startedBefore AND j.id <> :excludeId")
    int markStaleProcessingAsFailed(String reason, LocalDateTime now, LocalDateTime startedBefore, UUID excludeId);

    /** Beim Herunterfahren: den eigenen, gerade abgebrochenen Job als FAILED markieren. */
    @Modifying
    @Query("UPDATE XpengImportJob j SET j.status = 'FAILED', j.errorMessage = :reason, j.completedAt = :now "
            + "WHERE j.id = :id AND j.status = 'PROCESSING'")
    int markProcessingAsFailed(UUID id, String reason, LocalDateTime now);

    @Modifying
    long deleteAllByUserId(UUID userId);
}
