package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaChargingSessionGroupRepository extends JpaRepository<ChargingSessionGroupEntity, UUID> {

    List<ChargingSessionGroupEntity> findAllByCarId(UUID carId);

    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM ChargingSessionGroupEntity g
            WHERE g.dataSource = :dataSource
              AND g.carId IN (SELECT c.id FROM CarEntity c WHERE c.userId = :userId)
            """)
    void deleteAllByUserIdAndDataSource(@Param("userId") UUID userId, @Param("dataSource") String dataSource);

    /**
     * Findet eine offene Gruppe für ein Fahrzeug:
     * - session_end >= threshold (innerhalb Merge-Fenster)
     * - Gleicher Kalendertag: session_start liegt zwischen dayStart (inkl.) und dayEnd (exkl.)
     * - Gleiche data_source
     * Sortiert nach session_end DESC (jüngste zuerst), nimmt die aktuellste Gruppe.
     *
     * Kein CAST AS date — stattdessen range-Vergleich für H2 + PostgreSQL Kompatibilität.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChargingSessionGroupEntity g SET g.carId = :targetCarId WHERE g.id = :groupId")
    void updateCarId(@Param("groupId") UUID groupId, @Param("targetCarId") UUID targetCarId);

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
