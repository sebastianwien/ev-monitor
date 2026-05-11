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

    @Modifying
    @Query("UPDATE XpengImportJob j SET j.status = 'FAILED', j.errorMessage = :reason, j.completedAt = :now "
            + "WHERE j.status IN ('QUEUED','PROCESSING')")
    int markAllInFlightAsFailed(String reason, LocalDateTime now);
}
