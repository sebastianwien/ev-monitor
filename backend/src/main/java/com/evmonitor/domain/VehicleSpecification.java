package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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

    public VehicleSpecification(UUID id, String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                BigDecimal wltpRangeKm, BigDecimal wltpConsumptionKwhPer100km, WltpType wltpType,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.carBrand = carBrand;
        this.carModel = carModel;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.wltpRangeKm = wltpRangeKm;
        this.wltpConsumptionKwhPer100km = wltpConsumptionKwhPer100km;
        this.wltpType = wltpType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static VehicleSpecification createNew(String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                                  BigDecimal wltpRangeKm, BigDecimal wltpConsumptionKwhPer100km,
                                                  WltpType wltpType) {
        LocalDateTime now = LocalDateTime.now();
        return new VehicleSpecification(UUID.randomUUID(), carBrand, carModel, batteryCapacityKwh,
                wltpRangeKm, wltpConsumptionKwhPer100km, wltpType, now, now);
    }

    public UUID getId() {
        return id;
    }

    public String getCarBrand() {
        return carBrand;
    }

    public String getCarModel() {
        return carModel;
    }

    public BigDecimal getBatteryCapacityKwh() {
        return batteryCapacityKwh;
    }

    public BigDecimal getWltpRangeKm() {
        return wltpRangeKm;
    }

    public BigDecimal getWltpConsumptionKwhPer100km() {
        return wltpConsumptionKwhPer100km;
    }

    public WltpType getWltpType() {
        return wltpType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum WltpType {
        COMBINED,
        HIGHWAY,
        CITY
    }
}
