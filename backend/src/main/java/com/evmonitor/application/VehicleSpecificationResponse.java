package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.VehicleSpecification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleSpecificationResponse(
        UUID id,
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        BigDecimal officialRangeKm,
        BigDecimal officialConsumptionKwhPer100km,
        String wltpType,
        String ratingSource,
        String variantName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VehicleSpecificationResponse fromDomain(VehicleSpecification spec) {
        String variantName = null;
        try {
            variantName = CarBrand.CarModel.valueOf(spec.getCarModel())
                    .variantNameFor(spec.getBatteryCapacityKwh())
                    .orElse(null);
        } catch (IllegalArgumentException ignored) {
            // User-contributed entry with custom model key — no catalog match
        }
        return new VehicleSpecificationResponse(
                spec.getId(),
                spec.getCarBrand(),
                spec.getCarModel(),
                spec.getBatteryCapacityKwh(),
                spec.getOfficialRangeKm(),
                spec.getOfficialConsumptionKwhPer100km(),
                spec.getWltpType().name(),
                spec.getRatingSource().name(),
                variantName,
                spec.getCreatedAt(),
                spec.getUpdatedAt()
        );
    }
}
