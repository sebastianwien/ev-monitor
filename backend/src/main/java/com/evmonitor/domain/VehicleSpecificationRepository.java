package com.evmonitor.domain;

import java.math.BigDecimal;
import java.util.Optional;

public interface VehicleSpecificationRepository {
    VehicleSpecification save(VehicleSpecification vehicleSpecification);

    Optional<VehicleSpecification> findByCarBrandAndModelAndCapacityAndType(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        VehicleSpecification.WltpType wltpType
    );

    boolean existsByCarBrandAndModelAndCapacityAndType(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        VehicleSpecification.WltpType wltpType
    );
}
