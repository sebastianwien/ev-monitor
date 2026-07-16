package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CarBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaCarRepository extends JpaRepository<CarEntity, UUID> {
    List<CarEntity> findAllByUserId(UUID userId);

    List<CarEntity> findAllByModel(CarBrand.CarModel model);

    long countByUserId(UUID userId);

    List<CarEntity> findAllByVehicleSpecificationId(UUID vehicleSpecificationId);

    /**
     * Only public photos, newest first. Native query reads {@code model} as a raw string so an
     * unmappable enum value in one row (legacy data) can never break the whole query.
     */
    @Query(value = """
            SELECT c.id AS id, c.model AS model FROM car c
            WHERE c.image_public = true AND c.image_path IS NOT NULL
            ORDER BY c.updated_at DESC
            """, nativeQuery = true)
    List<PublicPhotoProjection> findPublicPhotoRefsNewestFirst();

    @Query(value = """
            SELECT c.id FROM car c
            WHERE c.model = :model AND c.image_public = true AND c.image_path IS NOT NULL
            ORDER BY c.updated_at DESC
            """, nativeQuery = true)
    List<UUID> findPublicPhotoCarIdsByModel(String model);

    interface PublicPhotoProjection {
        UUID getId();
        String getModel();
    }

    @Query(value = """
            SELECT c.* FROM car c
            WHERE EXISTS (
                SELECT 1 FROM ev_log el
                WHERE el.car_id = c.id
                  AND el.measurement_type = 'AT_VEHICLE'
            )
            AND NOT EXISTS (
                SELECT 1 FROM car_battery_soh_log s
                WHERE s.car_id = c.id
                  AND EXTRACT(YEAR FROM s.recorded_at) = EXTRACT(YEAR FROM CURRENT_DATE)
            )
            """, nativeQuery = true)
    List<CarEntity> findCarsNeedingSohDetection();
}
