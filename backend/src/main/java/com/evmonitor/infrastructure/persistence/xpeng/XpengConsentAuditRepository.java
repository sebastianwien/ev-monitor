package com.evmonitor.infrastructure.persistence.xpeng;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

public interface XpengConsentAuditRepository extends JpaRepository<XpengConsentAudit, UUID> {

    /** Kontoloeschung: Einwilligungs-Nachweis des Users mitentfernen (kein FK-CASCADE). */
    @Modifying
    @Transactional
    @Query("DELETE FROM XpengConsentAudit a WHERE a.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /** TTL-Purge: Nachweise, die aelter als der Schwellwert archiviert wurden. */
    @Modifying
    @Transactional
    @Query("DELETE FROM XpengConsentAudit a WHERE a.archivedAt < :threshold")
    int deleteByArchivedAtBefore(@Param("threshold") LocalDateTime threshold);
}
