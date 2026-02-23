package com.evmonitor.application;

import com.evmonitor.domain.VehicleSpecification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleSpecificationResponse(
        UUID id,
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        BigDecimal wltpRangeKm,
        BigDecimal wltpConsumptionKwhPer100km,
        String wltpType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VehicleSpecificationResponse fromDomain(VehicleSpecification spec) {
        return new VehicleSpecificationResponse(
                spec.getId(),
                spec.getCarBrand(),
                spec.getCarModel(),
                spec.getBatteryCapacityKwh(),
                spec.getWltpRangeKm(),
                spec.getWltpConsumptionKwhPer100km(),
                spec.getWltpType().name(),
                spec.getCreatedAt(),
                spec.getUpdatedAt()
        );
    }
}
