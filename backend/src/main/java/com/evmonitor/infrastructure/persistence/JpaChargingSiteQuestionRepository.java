package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Die offenen Ladungen einer Frage stehen in beiden Abfragen gleich da: Ladungen des Nutzers in der
 * Zelle mit der Ladeart, ohne Säule, nicht privat, nicht gelöscht, nicht von einer Wallbox.
 */
public interface JpaChargingSiteQuestionRepository extends JpaRepository<ChargingSiteQuestionEntity, UUID> {

    boolean existsByUserIdAndGeohashAndChargingType(UUID userId, String geohash, String chargingType);

    Optional<ChargingSiteQuestionEntity> findByIdAndUserId(UUID id, UUID userId);

    interface OpenRow {
        UUID getQuestionId();
        long getLogCount();
        LocalDateTime getLastLoggedAt();
    }

    @Query("""
            SELECT q.id AS questionId, COUNT(e) AS logCount, MAX(e.loggedAt) AS lastLoggedAt
            FROM ChargingSiteQuestionEntity q
            JOIN CarEntity c ON c.userId = q.userId AND c.deletedAt IS NULL
            JOIN EvLogEntity e ON e.carId = c.id
            WHERE q.userId = :userId AND q.status = 'OPEN'
              AND e.geohash LIKE CONCAT(q.geohash, '%')
              AND COALESCE(e.chargingType, '') = q.chargingType
              AND e.chargingSiteId IS NULL
              AND (e.publicCharging IS NULL OR e.publicCharging = true)
              AND e.dataSource NOT IN ('WALLBOX_GOE', 'WALLBOX_OCPP')
              AND e.deletedAt IS NULL
            GROUP BY q.id
            ORDER BY COUNT(e) DESC, MAX(e.loggedAt) DESC
            """)
    List<OpenRow> findOpenWithLogs(@Param("userId") UUID userId);

    @Query("""
            SELECT e.id FROM EvLogEntity e
            JOIN CarEntity c ON e.carId = c.id AND c.deletedAt IS NULL
            WHERE c.userId = :userId
              AND e.geohash LIKE CONCAT(:geohash, '%')
              AND COALESCE(e.chargingType, '') = :chargingType
              AND e.chargingSiteId IS NULL
              AND (e.publicCharging IS NULL OR e.publicCharging = true)
              AND e.dataSource NOT IN ('WALLBOX_GOE', 'WALLBOX_OCPP')
              AND e.deletedAt IS NULL
            ORDER BY e.loggedAt
            """)
    List<UUID> findOpenLogIds(@Param("userId") UUID userId, @Param("geohash") String geohash,
                              @Param("chargingType") String chargingType);

    @Modifying
    @Query("""
            UPDATE ChargingSiteQuestionEntity q SET q.status = :status, q.answeredAt = :now
            WHERE q.id = :id AND q.userId = :userId AND q.status = 'OPEN'
            """)
    int close(@Param("id") UUID id, @Param("userId") UUID userId, @Param("status") String status,
              @Param("now") LocalDateTime now);
}
