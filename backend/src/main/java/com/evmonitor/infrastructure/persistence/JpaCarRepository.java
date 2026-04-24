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
