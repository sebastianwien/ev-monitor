package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Car {
    private final UUID id;
    private final UUID userId;
    private final CarBrand.CarModel model;
    private final Integer year;
    private final String licensePlate;
    private final String trim;
    private final BigDecimal batteryCapacityKwh;
    private final BigDecimal powerKw;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Car(UUID id, UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.model = model;
        this.year = year;
        this.licensePlate = licensePlate;
        this.trim = trim;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.powerKw = powerKw;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Car createNew(UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw) {
        LocalDateTime now = LocalDateTime.now();
        return new Car(UUID.randomUUID(), userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw, now, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public CarBrand.CarModel getModel() {
        return model;
    }

    public Integer getYear() {
        return year;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getTrim() {
        return trim;
    }

    public BigDecimal getBatteryCapacityKwh() {
        return batteryCapacityKwh;
    }

    public BigDecimal getPowerKw() {
        return powerKw;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
