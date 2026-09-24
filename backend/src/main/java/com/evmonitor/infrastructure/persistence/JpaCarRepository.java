package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CarBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaCarRepository extends JpaRepository<CarEntity, UUID> {
    Optional<CarEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<CarEntity> findAllByUserIdAndDeletedAtIsNull(UUID userId);

    List<CarEntity> findAllByModelAndDeletedAtIsNull(CarBrand.CarModel model);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    List<CarEntity> findAllByVehicleSpecificationIdAndDeletedAtIsNull(UUID vehicleSpecificationId);

    List<CarEntity> findAllByDeletedAtBefore(LocalDateTime cutoff);

    /** Inklusive soft-gelöschter - nur für DSGVO-Pfade. */
    List<CarEntity> findAllByUserId(UUID userId);

    @Query(value = """
            SELECT c.* FROM car c
            WHERE c.deleted_at IS NULL
            AND EXISTS (
                SELECT 1 FROM ev_log el
                WHERE el.deleted_at IS NULL AND el.car_id = c.id
                  AND el.measurement_type = 'AT_VEHICLE'
            )
            AND NOT EXISTS (
                SELECT 1 FROM car_battery_soh_log s
                WHERE s.car_id = c.id
                  AND EXTRACT(YEAR FROM s.recorded_at) = EXTRACT(YEAR FROM CURRENT_DATE)
            )
            """, nativeQuery = true)
    List<CarEntity> findCarsNeedingSohDetection();

    // ── Oeffentliche Fahrzeugseite (share_token) ─────────────────────────────

    @Query("SELECT c.shareToken FROM CarEntity c WHERE c.id = :id")
    Optional<String> findShareToken(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CarEntity c SET c.shareToken = :token, c.shareCreatedAt = :createdAt WHERE c.id = :id")
    int updateShareToken(@Param("id") UUID id, @Param("token") String token, @Param("createdAt") LocalDateTime createdAt);

    @Modifying
    @Query("UPDATE CarEntity c SET c.shareToken = NULL, c.shareCreatedAt = NULL WHERE c.id = :id")
    int clearShareToken(@Param("id") UUID id);

    Optional<CarEntity> findByShareTokenAndDeletedAtIsNull(String shareToken);
}
