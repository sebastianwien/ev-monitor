package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaEvLogRepository extends JpaRepository<EvLogEntity, UUID> {
    Optional<EvLogEntity> findByIdAndCarId(UUID id, UUID carId);

    List<EvLogEntity> findAllByCarId(UUID carId);

    List<EvLogEntity> findAllByCarIdOrderByLoggedAtDesc(UUID carId, org.springframework.data.domain.Pageable pageable);

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

}
