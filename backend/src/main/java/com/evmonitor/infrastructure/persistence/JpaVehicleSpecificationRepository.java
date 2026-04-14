package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaVehicleSpecificationRepository extends JpaRepository<VehicleSpecificationEntity, UUID> {

    Optional<VehicleSpecificationEntity> findByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpTypeAndRatingSource(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        String wltpType,
        String ratingSource
    );

    boolean existsByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpTypeAndRatingSource(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        String wltpType,
        String ratingSource
    );

    List<VehicleSpecificationEntity> findByCarModelOrderByBatteryCapacityKwhAsc(String carModel);

    List<VehicleSpecificationEntity> findByCarModelAndRatingSourceOrderByBatteryCapacityKwhAsc(
        String carModel,
        String ratingSource
    );
}
