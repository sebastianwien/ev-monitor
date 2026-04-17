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

    @Query("""
        SELECT e FROM EvLogEntity e
        WHERE e.carId = :carId
          AND (e.measurementType = 'AT_VEHICLE' OR e.kwhAtVehicle IS NOT NULL)
          AND e.socBeforeChargePercent IS NOT NULL
          AND e.socAfterChargePercent IS NOT NULL
          AND e.includeInStatistics = true
          AND e.supersededBy IS NULL
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findRecentAtVehicleLogsWithSoc(
            @Param("carId") UUID carId,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT e.geohash, e.kwhCharged FROM EvLogEntity e WHERE e.carId = :carId AND e.geohash IS NOT NULL AND e.supersededBy IS NULL")
    List<Object[]> findGeohashDataByCarId(@Param("carId") UUID carId);

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

    boolean existsByCarIdAndLoggedAtAndKwhCharged(UUID carId, LocalDateTime loggedAt, BigDecimal kwhCharged);

    Optional<EvLogEntity> findByCarIdAndLoggedAt(UUID carId, LocalDateTime loggedAt);

    @Query("SELECT e FROM EvLogEntity e WHERE e.geohash IS NOT NULL AND e.temperatureCelsius IS NULL")
    List<EvLogEntity> findAllWithGeohashAndNoTemperature();

    @Query("""
        SELECT e FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id
        WHERE c.userId = :userId
          AND e.geohash = :geohash
          AND e.costEur IS NOT NULL AND e.costEur > 0
          AND e.kwhCharged IS NOT NULL AND e.kwhCharged > 0
          AND e.supersededBy IS NULL
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findRecentByUserIdAndGeohash(@Param("userId") UUID userId, @Param("geohash") String geohash,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT e FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id
        WHERE c.userId = :userId
          AND e.geohash = :geohash
          AND e.chargingProviderId IS NOT NULL
          AND e.supersededBy IS NULL
        ORDER BY e.loggedAt DESC
        """)
    List<EvLogEntity> findRecentWithProviderByUserIdAndGeohash(@Param("userId") UUID userId, @Param("geohash") String geohash,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT e FROM EvLogEntity e
            WHERE e.carId = :carId
              AND e.publicCharging = false
              AND e.supersededBy IS NULL
              AND e.loggedAt >= :from
              AND e.loggedAt < :to
            ORDER BY e.loggedAt ASC
            """)
    List<EvLogEntity> findHomeChargingSessionsForExport(
            @Param("carId") UUID carId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

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

    @Modifying
    @Query("UPDATE EvLogEntity e SET e.includeInStatistics = :includeInStatistics, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    void updateIncludeInStatistics(@Param("id") UUID id, @Param("includeInStatistics") boolean includeInStatistics);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EvLogEntity e SET e.carId = :targetCarId WHERE e.id = :logId")
    void updateCarIdForLog(@Param("logId") UUID logId, @Param("targetCarId") UUID targetCarId);

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
            WITH filtered AS (
                SELECT l.id,
                       l.kwh_charged,
                       l.cost_eur,
                       c.user_id,
                       l.odometer_km,
                       LAG(l.odometer_km) OVER (PARTITION BY l.car_id ORDER BY l.logged_at) AS prev_odometer
                FROM ev_log l
                JOIN car c ON c.id = l.car_id
                WHERE c.model = :model
                  AND (l.include_in_statistics = true
                       OR (:isSeedUser = true
                           AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            )
            SELECT
                COUNT(id) FILTER (WHERE prev_odometer IS NULL OR odometer_km IS NULL OR odometer_km != prev_odometer) AS log_count,
                COUNT(DISTINCT user_id)                                                                                AS unique_contributors,
                AVG(CASE WHEN cost_eur > 0 THEN cost_eur / NULLIF(kwh_charged, 0) END)                               AS avg_cost_per_kwh,
                AVG(kwh_charged) FILTER (WHERE prev_odometer IS NULL OR odometer_km IS NULL OR odometer_km != prev_odometer) AS avg_kwh_per_session
            FROM filtered
            """, nativeQuery = true)
    Object[] findPublicBasicStatsByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

    /**
     * Average DC charging power (kW) for a model, calculated from real DC fast-charging sessions.
     * Uses energy-weighted approach: total kWh / total hours charging.
     * Only includes sessions with charging_type = 'DC' and at least 5 sessions to filter outliers.
     * Returns null if fewer than 5 qualifying DC sessions exist.
     */
    @Query(value = """
            SELECT
                CASE WHEN COUNT(*) >= 5
                     THEN SUM(kwh_charged) / NULLIF(SUM(charge_duration_minutes) / 60.0, 0)
                     ELSE NULL
                END
            FROM ev_log l
            JOIN car c ON c.id = l.car_id
            WHERE c.model = :model
              AND l.charge_duration_minutes > 0
              AND l.kwh_charged > 0
              AND l.charging_type = 'DC'
              AND (l.include_in_statistics = true
                   OR (:isSeedUser = true
                       AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            """, nativeQuery = true)
    BigDecimal findAvgDcChargingPowerKwByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

    /**
     * Returns AC and DC average cost per kWh for a model.
     * Only included if at least 5 sessions with cost data exist per type.
     * Returns: [acAvgCostPerKwh, acCount, dcAvgCostPerKwh, dcCount]
     */
    @Query(value = """
            SELECT
                CASE WHEN COUNT(*) FILTER (WHERE l.charging_type = 'AC' AND l.cost_eur > 0 AND l.kwh_charged > 0) >= 5
                     THEN AVG(l.cost_eur / l.kwh_charged) FILTER (WHERE l.charging_type = 'AC' AND l.cost_eur > 0 AND l.kwh_charged > 0)
                     ELSE NULL END AS ac_avg_cost,
                COUNT(*) FILTER (WHERE l.charging_type = 'AC' AND l.cost_eur > 0 AND l.kwh_charged > 0) AS ac_count,
                CASE WHEN COUNT(*) FILTER (WHERE l.charging_type = 'DC' AND l.cost_eur > 0 AND l.kwh_charged > 0) >= 5
                     THEN AVG(l.cost_eur / l.kwh_charged) FILTER (WHERE l.charging_type = 'DC' AND l.cost_eur > 0 AND l.kwh_charged > 0)
                     ELSE NULL END AS dc_avg_cost,
                COUNT(*) FILTER (WHERE l.charging_type = 'DC' AND l.cost_eur > 0 AND l.kwh_charged > 0) AS dc_count
            FROM ev_log l
            JOIN car c ON c.id = l.car_id
            WHERE c.model = :model
              AND (l.include_in_statistics = true
                   OR (:isSeedUser = true
                       AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
            """, nativeQuery = true)
    Object[] findAcDcCostStatsByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

}
