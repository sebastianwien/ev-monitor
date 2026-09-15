package com.evmonitor.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaChargingSiteRepository extends JpaRepository<ChargingSiteEntity, UUID> {

    Optional<ChargingSiteEntity> findByGeohashAndNameKey(String geohash, String nameKey);

    interface UsageRow {
        ChargingSiteEntity getSite();
        LocalDateTime getLastUsedAt();
        long getUsageCount();
    }

    /** Standorte des Nutzers ueber alle seine Autos, zuletzt genutzte zuerst. */
    @Query("""
            SELECT s AS site, MAX(l.loggedAt) AS lastUsedAt, COUNT(l) AS usageCount
            FROM EvLogEntity l
            JOIN ChargingSiteEntity s ON l.chargingSiteId = s.id
            JOIN CarEntity c ON l.carId = c.id
            WHERE c.userId = :userId
            GROUP BY s
            ORDER BY MAX(l.loggedAt) DESC
            """)
    List<UsageRow> findRecentlyUsedByUser(@Param("userId") UUID userId, Pageable page);
}
