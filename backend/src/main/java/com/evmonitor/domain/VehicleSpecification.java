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
    private final BigDecimal officialRangeKm;
    private final BigDecimal officialConsumptionKwhPer100km;
    private final WltpType wltpType;
    private final RatingSource ratingSource;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static VehicleSpecification createNew(String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                                  BigDecimal rangeKm, BigDecimal consumptionKwhPer100km,
                                                  WltpType cycleType, RatingSource ratingSource) {
        LocalDateTime now = LocalDateTime.now();
        return new VehicleSpecification(UUID.randomUUID(), carBrand, carModel, batteryCapacityKwh,
                rangeKm, consumptionKwhPer100km, cycleType, ratingSource, now, now);
    }

    /** Backward-compat factory that defaults to WLTP. */
    public static VehicleSpecification createNew(String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                                  BigDecimal rangeKm, BigDecimal consumptionKwhPer100km,
                                                  WltpType cycleType) {
        return createNew(carBrand, carModel, batteryCapacityKwh, rangeKm, consumptionKwhPer100km, cycleType, RatingSource.WLTP);
    }

    public enum WltpType {
        COMBINED,
        HIGHWAY,
        CITY
    }

    public enum RatingSource {
        WLTP,
        EPA
    }
}
