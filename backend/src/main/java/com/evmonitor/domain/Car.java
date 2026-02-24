package com.evmonitor.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final LocalDate registrationDate;
    private final LocalDate deregistrationDate;
    private final CarStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Car(UUID id, UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw,
            LocalDate registrationDate, LocalDate deregistrationDate, CarStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.model = model;
        this.year = year;
        this.licensePlate = licensePlate;
        this.trim = trim;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.powerKw = powerKw;
        this.registrationDate = registrationDate;
        this.deregistrationDate = deregistrationDate;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Car createNew(UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate registrationDate = LocalDate.of(year, 1, 1); // Default: Jan 1st of car year
        return new Car(UUID.randomUUID(), userId, model, year, licensePlate, trim,
                batteryCapacityKwh, powerKw, registrationDate, null, CarStatus.ACTIVE, now, now);
    }

    public Car deregister(LocalDate deregistrationDate) {
        return new Car(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, CarStatus.INACTIVE, createdAt, LocalDateTime.now());
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

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public LocalDate getDeregistrationDate() {
        return deregistrationDate;
    }

    public CarStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
