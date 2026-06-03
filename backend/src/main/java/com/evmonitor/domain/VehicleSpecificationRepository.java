package com.evmonitor.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleSpecificationRepository {
    VehicleSpecification save(VehicleSpecification vehicleSpecification);

    Optional<VehicleSpecification> findById(UUID id);

    List<VehicleSpecification> findByCarModelOrderByBatteryCapacityKwhAsc(String carModel);

    Optional<VehicleSpecification> findByCarBrandAndModelAndCapacityAndType(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        VehicleSpecification.WltpType wltpType
    );

    Optional<VehicleSpecification> findByCarBrandAndModelAndCapacityAndTypeAndSource(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        VehicleSpecification.WltpType wltpType,
        VehicleSpecification.RatingSource ratingSource
    );

    boolean existsByCarBrandAndModelAndCapacityAndTypeAndSource(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        VehicleSpecification.WltpType wltpType,
        VehicleSpecification.RatingSource ratingSource
    );

    boolean existsByCarBrandAndModelAndCapacityAndVariantNameAndTypeAndSource(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        String variantName,
        VehicleSpecification.WltpType wltpType,
        VehicleSpecification.RatingSource ratingSource
    );
}
