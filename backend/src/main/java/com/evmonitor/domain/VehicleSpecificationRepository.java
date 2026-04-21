package com.evmonitor.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface VehicleSpecificationRepository {
    VehicleSpecification save(VehicleSpecification vehicleSpecification);

    Optional<VehicleSpecification> findById(UUID id);

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
}
