package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaVehicleSpecificationRepository extends JpaRepository<VehicleSpecificationEntity, UUID> {

    Optional<VehicleSpecificationEntity> findByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpType(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        String wltpType
    );

    boolean existsByCarBrandAndCarModelAndBatteryCapacityKwhAndWltpType(
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        String wltpType
    );
}
