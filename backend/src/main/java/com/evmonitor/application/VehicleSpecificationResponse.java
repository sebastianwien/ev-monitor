package com.evmonitor.application;

import com.evmonitor.domain.VehicleSpecification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleSpecificationResponse(
        UUID id,
        String carBrand,
        String carModel,
        BigDecimal batteryCapacityKwh,
        BigDecimal netBatteryCapacityKwh,
        BigDecimal officialRangeKm,
        BigDecimal officialConsumptionKwhPer100km,
        String wltpType,
        String ratingSource,
        String variantName,
        LocalDate availableFrom,
        LocalDate availableTo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VehicleSpecificationResponse fromDomain(VehicleSpecification spec) {
        String variantName = (spec.getVariantName() != null && !spec.getVariantName().isBlank())
                ? spec.getVariantName()
                : null;
        return new VehicleSpecificationResponse(
                spec.getId(),
                spec.getCarBrand(),
                spec.getCarModel(),
                spec.getBatteryCapacityKwh(),
                spec.getNetBatteryCapacityKwh(),
                spec.getOfficialRangeKm(),
                spec.getOfficialConsumptionKwhPer100km(),
                spec.getWltpType().name(),
                spec.getRatingSource().name(),
                variantName,
                spec.getAvailableFrom(),
                spec.getAvailableTo(),
                spec.getCreatedAt(),
                spec.getUpdatedAt()
        );
    }
}
