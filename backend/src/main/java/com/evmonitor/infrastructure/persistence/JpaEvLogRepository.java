package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
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

    List<EvLogEntity> findAllByCarId(UUID carId);

    @Query("SELECT e FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id WHERE c.userId = :userId")
    List<EvLogEntity> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(e) FROM EvLogEntity e JOIN CarEntity c ON e.carId = c.id WHERE c.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    boolean existsByCarIdAndLoggedAtBetween(UUID carId, LocalDateTime start, LocalDateTime end);

    boolean existsByCarIdAndLoggedAtAndDataSource(UUID carId, LocalDateTime loggedAt, String dataSource);

    Optional<EvLogEntity> findByCarIdAndLoggedAt(UUID carId, LocalDateTime loggedAt);

    @Query("SELECT e FROM EvLogEntity e WHERE e.geohash IS NOT NULL AND e.temperatureCelsius IS NULL")
    List<EvLogEntity> findAllWithGeohashAndNoTemperature();

    /**
     * Aggregated basic stats for a car model.
     * Returns: [logCount, uniqueContributors, avgCostPerKwh, avgKwhPerSession]
     * Demo Mode: If isSeedUser=true, includes ALL seed data (from all seed users), not just current user.
     * Uses native SQL because JPQL doesn't support dividing two columns directly in aggregation.
     */
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

    /**
     * Average real-world consumption from consecutive odometer readings.
     * Demo Mode: If isSeedUser=true, includes ALL seed data (from all seed users).
     * Uses LAG() window function to compute km driven between charges.
     * Sanity filter: only 5–2000 km between consecutive charges (filters outliers).
     */
    @Query(value = """
            WITH log_pairs AS (
                SELECT
                    l.kwh_charged,
                    l.odometer_km,
                    LAG(l.odometer_km) OVER (PARTITION BY l.car_id ORDER BY l.logged_at) AS prev_odometer
                FROM ev_log l
                JOIN car c ON c.id = l.car_id
                WHERE c.model = :model
                  AND (l.include_in_statistics = true
                       OR (:isSeedUser = true
                           AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
                  AND l.odometer_km IS NOT NULL
            )
            SELECT AVG(kwh_charged / (odometer_km - prev_odometer) * 100)
            FROM log_pairs
            WHERE prev_odometer IS NOT NULL
              AND (odometer_km - prev_odometer) BETWEEN 5 AND 2000
            """, nativeQuery = true)
    BigDecimal findAvgConsumptionByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);

    /**
     * Seasonal breakdown: km driven, consumption, and log count per season.
     * Returns: [summerKm, winterKm, summerConsumption, winterConsumption, summerLogCount, winterLogCount]
     * Summer: Apr-Sep (months 4-9), Winter: Oct-Mar (months 1-3, 10-12)
     * Demo Mode: If isSeedUser=true, includes ALL seed data (from all seed users).
     */
    @Query(value = """
            WITH log_pairs AS (
                SELECT
                    l.logged_at,
                    l.kwh_charged,
                    l.odometer_km,
                    LAG(l.odometer_km) OVER (PARTITION BY l.car_id ORDER BY l.logged_at) AS prev_odometer
                FROM ev_log l
                JOIN car c ON c.id = l.car_id
                WHERE c.model = :model
                  AND (l.include_in_statistics = true
                       OR (:isSeedUser = true
                           AND c.user_id IN (SELECT id FROM app_user WHERE is_seed_data = true)))
                  AND l.odometer_km IS NOT NULL
            ),
            km_per_season AS (
                SELECT
                    CASE
                        WHEN EXTRACT(MONTH FROM logged_at) BETWEEN 4 AND 9 THEN 'SUMMER'
                        ELSE 'WINTER'
                    END AS season,
                    kwh_charged,
                    (odometer_km - prev_odometer) AS km_driven
                FROM log_pairs
                WHERE prev_odometer IS NOT NULL
                  AND (odometer_km - prev_odometer) BETWEEN 5 AND 2000
            )
            SELECT
                COALESCE(SUM(CASE WHEN season = 'SUMMER' THEN km_driven ELSE 0 END), 0) AS summer_km,
                COALESCE(SUM(CASE WHEN season = 'WINTER' THEN km_driven ELSE 0 END), 0) AS winter_km,
                COALESCE(AVG(CASE WHEN season = 'SUMMER' THEN (kwh_charged / km_driven * 100) END), NULL) AS summer_consumption,
                COALESCE(AVG(CASE WHEN season = 'WINTER' THEN (kwh_charged / km_driven * 100) END), NULL) AS winter_consumption,
                COALESCE(SUM(CASE WHEN season = 'SUMMER' THEN 1 ELSE 0 END), 0) AS summer_log_count,
                COALESCE(SUM(CASE WHEN season = 'WINTER' THEN 1 ELSE 0 END), 0) AS winter_log_count
            FROM km_per_season
            """, nativeQuery = true)
    Object[] findSeasonalDistributionByModel(
            @Param("model") String model,
            @Param("isSeedUser") boolean isSeedUser);
}
