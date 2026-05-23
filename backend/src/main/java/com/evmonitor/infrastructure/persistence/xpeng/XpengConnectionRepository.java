package com.evmonitor.infrastructure.persistence.xpeng;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface XpengConnectionRepository extends JpaRepository<XpengConnection, UUID> {
    Optional<XpengConnection> findByCarId(UUID carId);
    Optional<XpengConnection> findByRoutingToken(UUID routingToken);
    List<XpengConnection> findAllByUserIdAndConsentRevokedAtIsNull(UUID userId);

    @Query("""
        SELECT c FROM XpengConnection c
        WHERE c.autoSyncEnabled = true
          AND c.consentRevokedAt IS NULL
          AND (c.lastRequestSentAt IS NULL OR c.lastRequestSentAt < :threshold)
        """)
    List<XpengConnection> findDueForAutoRequest(LocalDateTime threshold);
}
