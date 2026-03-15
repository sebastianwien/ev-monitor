package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaEvLogRepository extends JpaRepository<EvLogEntity, UUID> {
    Optional<EvLogEntity> findByIdAndCarId(UUID id, UUID carId);

    @Query("SELECT e FROM EvLogEntity e WHERE e.carId = :carId AND e.supersededBy IS NULL")
    List<EvLogEntity> findAllByCarId(@Param("carId") UUID carId);

    @Query("SELECT e FROM EvLogEntity e WHERE e.carId IN :carIds AND e.supersededBy IS NULL")
    List<EvLogEntity> findAllByCarIdIn(@Param("carIds") List<UUID> carIds);

    @Query("SELECT e FROM EvLogEntity e WHERE e.carId = :carId AND e.supersededBy IS NULL ORDER BY e.loggedAt DESC")
    List<EvLogEntity> findAllByCarIdOrderByLoggedAtDesc(@Param("carId") UUID carId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT e FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id WHERE c.userId = :userId AND e.supersededBy IS NULL")
    List<EvLogEntity> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(e) FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id WHERE c.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    boolean existsByCarIdAndLoggedAtBetween(UUID carId, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndOdometerKmAndLoggedAtBetween(UUID carId, Integer odometerKm, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndLoggedAtAndDataSource(UUID carId, LocalDateTime loggedAt, String dataSource);

    Optional<EvLogEntity> findByCarIdAndLoggedAt(UUID carId, LocalDateTime loggedAt);

    @Query("SELECT e FROM EvLogEntity e WHERE e.geohash IS NOT NULL AND e.temperatureCelsius IS NULL")
    List<EvLogEntity> findAllWithGeohashAndNoTemperature();

    @Modifying
    @Query("DELETE FROM EvLogEntity e WHERE e.carId IN (SELECT c.id FROM CarEntity c WHERE c.userId = :userId) AND e.dataSource = :dataSource")
    void deleteAllByUserIdAndDataSource(@Param("userId") UUID userId, @Param("dataSource") String dataSource);

    @Modifying
    @Query("DELETE FROM EvLogEntity e WHERE e.carId IN (SELECT c.id FROM CarEntity c WHERE c.userId = :userId) AND e.dataSource IN :dataSources")
    void deleteAllByUserIdAndDataSourceIn(@Param("userId") UUID userId, @Param("dataSources") List<String> dataSources);

    @Query("""
            SELECT e FROM EvLogEntity e
            WHERE e.carId = :carId
              AND e.dataSource != 'USER_LOGGED'
              AND e.supersededBy IS NULL
              AND e.loggedAt BETWEEN :from AND :to
              AND e.kwhCharged BETWEEN :kwhMin AND :kwhMax
            """)
    List<EvLogEntity> findImportLogsInTimeWindow(
            @Param("carId") UUID carId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("kwhMin") BigDecimal kwhMin,
            @Param("kwhMax") BigDecimal kwhMax);

    // Returns a List (not Optional) to avoid NonUniqueResultException if the user has
    // multiple USER_LOGGED entries in the time window (e.g. corrected a previous entry).
    // Callers take the first match.
    @Query("""
            SELECT e FROM EvLogEntity e
            WHERE e.carId = :carId
              AND e.dataSource = 'USER_LOGGED'
              AND e.loggedAt BETWEEN :from AND :to
              AND e.kwhCharged BETWEEN :kwhMin AND :kwhMax
            ORDER BY e.loggedAt DESC
            """)
    List<EvLogEntity> findUserLoggedInTimeWindow(
            @Param("carId") UUID carId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("kwhMin") BigDecimal kwhMin,
            @Param("kwhMax") BigDecimal kwhMax);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.supersededBy = :supersededById WHERE e.id = :id")
    void markAsSuperseded(@Param("id") UUID id, @Param("supersededById") UUID supersededById);

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.supersededBy = NULL WHERE e.supersededBy = :supersededById")
    void clearSupersededByReferences(@Param("supersededById") UUID supersededById);

    /**
     * Verknüpft einen Log mit einer Session-Gruppe und setzt include_in_statistics=false.
     * Sub-Sessions dürfen nicht einzeln in Statistiken einfließen — die Gruppe aggregiert.
     */
    @Modifying
    @Query("UPDATE EvLogEntity e SET e.sessionGroupId = :groupId, e.includeInStatistics = false WHERE e.id = :id")
    void setSessionGroupId(@Param("id") UUID id, @Param("groupId") UUID groupId);

    @Query("SELECT e FROM EvLogEntity e WHERE e.sessionGroupId = :groupId ORDER BY e.loggedAt ASC")
    List<EvLogEntity> findAllBySessionGroupId(@Param("groupId") UUID groupId);

    @Query("SELECT e FROM EvLogEntity e WHERE e.carId = :carId AND e.supersededBy IS NULL AND e.sessionGroupId IS NULL")
    List<EvLogEntity> findAllByCarIdExcludingSubSessions(@Param("carId") UUID carId);

    /**
     * Aggregated basic stats for a car model.
     * Returns: [logCount, uniqueContributors, avgCostPerKwh, avgKwhPerSession]
     * Demo Mode: If isSeedUser=true, includes ALL seed data (from all seed users), not just current user.
     * Uses native SQL because JPQL doesn't support dividing two columns directly in aggregation.
     */
    @Query(value = """
            WITH ranked AS (
                SELECT
                    odometer_km,
                    kwh_charged,
                    soc_after_charge_percent,
                    LAG(odometer_km)              OVER (PARTITION BY car_id ORDER BY logged_at) AS prev_odometer,
                    LAG(soc_after_charge_percent) OVER (PARTITION BY car_id ORDER BY logged_at) AS prev_soc
                FROM ev_log
                WHERE include_in_statistics = true
            )
            SELECT COUNT(*) FROM ranked
            WHERE odometer_km IS NOT NULL
              AND kwh_charged IS NOT NULL
              AND soc_after_charge_percent IS NOT NULL
              AND prev_odometer IS NOT NULL
              AND prev_soc IS NOT NULL
              AND (odometer_km - prev_odometer) >= 20
            """, nativeQuery = true)
    long countValidTrips();

    @Query(value = """
            SELECT
                COUNT(l.id)                                            AS log_count,
                COUNT(DISTINCT c.user_id)                              AS unique_contributors,
                AVG(l.cost_eur / NULLIF(l.kwh_charged, 0))            AS avg_cost_per_kwh,
                AVG(l.kwh_charged)                                     AS avg_kwh_per_session
            FROM ev_log l
            JOIN car c ON c.id = l.car_id
            WHERE c.model = :model
              AND (l.include_in_statistics = true
                   OR (:isSeedUser = true
                       AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            """, nativeQuery = true)
    Object[] findPublicBasicStatsByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

}
