package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaVehicleSpecificationRepository extends JpaRepository<VehicleSpecificationEntity, UUID> {

    List<VehicleSpecificationEntity> findByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpTypeAndRatingSource(
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

    boolean existsByCarBrandAndCarModelAndBatteryCapacityKwhAndVariantNameAndWltpTypeAndRatingSource(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        String variantName,
        String wltpType,
        String ratingSource
    );

    List<VehicleSpecificationEntity> findByCarModelOrderByBatteryCapacityKwhAsc(String carModel);

    /** All specs of one rating source, for lists that need every model at once (one query instead of one per model). */
    List<VehicleSpecificationEntity> findByRatingSource(String ratingSource);

    List<VehicleSpecificationEntity> findByCarModelAndRatingSourceOrderByBatteryCapacityKwhAsc(
        String carModel,
        String ratingSource
    );

    List<VehicleSpecificationEntity> findByCarBrandAndCarModelAndWltpTypeAndRatingSourceOrderByBatteryCapacityKwhAsc(
        String carBrand,
        String carModel,
        String wltpType,
        String ratingSource
    );
}
