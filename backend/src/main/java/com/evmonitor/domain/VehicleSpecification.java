package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VehicleSpecification {
    private final UUID id;
    private final String carBrand;
    private final String carModel;
    private final BigDecimal batteryCapacityKwh;
    private final BigDecimal wltpRangeKm;
    private final BigDecimal wltpConsumptionKwhPer100km;
    private final WltpType wltpType;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static VehicleSpecification createNew(String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                                  BigDecimal wltpRangeKm, BigDecimal wltpConsumptionKwhPer100km,
                                                  WltpType wltpType) {
        LocalDateTime now = LocalDateTime.now();
        return new VehicleSpecification(UUID.randomUUID(), carBrand, carModel, batteryCapacityKwh,
                wltpRangeKm, wltpConsumptionKwhPer100km, wltpType, now, now);
    }

    public enum WltpType {
        COMBINED,
        HIGHWAY,
        CITY
    }
}
