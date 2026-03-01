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

    boolean existsByCarIdAndLoggedAtBetween(UUID carId, LocalDateTime start, LocalDateTime end);

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
}
