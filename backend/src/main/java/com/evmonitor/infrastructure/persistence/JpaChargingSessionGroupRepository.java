package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaChargingSessionGroupRepository extends JpaRepository<ChargingSessionGroupEntity, UUID> {

    List<ChargingSessionGroupEntity> findAllByCarId(UUID carId);

    /**
     * Findet eine offene Gruppe für ein Fahrzeug:
     * - session_end >= threshold (innerhalb Merge-Fenster)
     * - Gleicher Kalendertag: session_start liegt zwischen dayStart (inkl.) und dayEnd (exkl.)
     * - Gleiche data_source
     * Sortiert nach session_end DESC (jüngste zuerst), nimmt die aktuellste Gruppe.
     *
     * Kein CAST AS date — stattdessen range-Vergleich für H2 + PostgreSQL Kompatibilität.
     */
    @Query("""
            SELECT g FROM ChargingSessionGroupEntity g
            WHERE g.carId = :carId
              AND g.dataSource = :dataSource
              AND g.sessionEnd >= :threshold
              AND g.sessionStart >= :dayStart
              AND g.sessionStart < :dayEnd
            ORDER BY g.sessionEnd DESC
            """)
    List<ChargingSessionGroupEntity> findOpenGroupsForCar(
            @Param("carId") UUID carId,
            @Param("threshold") LocalDateTime threshold,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("dataSource") String dataSource);
}
