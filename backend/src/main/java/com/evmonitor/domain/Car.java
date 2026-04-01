package com.evmonitor.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    private final String imagePath;
    private final boolean imagePublic;
    private final boolean isPrimary;
    private final BigDecimal batteryDegradationPercent;
    private final boolean isBusinessCar;
    private final boolean hasHeatPump;

    public Car(UUID id, UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw,
            LocalDate registrationDate, LocalDate deregistrationDate, CarStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt, String imagePath, boolean imagePublic,
            boolean isPrimary) {
        this(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, status, createdAt, updatedAt, imagePath,
                imagePublic, isPrimary, null, false, false);
    }

    public Car(UUID id, UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw,
            LocalDate registrationDate, LocalDate deregistrationDate, CarStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt, String imagePath, boolean imagePublic,
            boolean isPrimary, BigDecimal batteryDegradationPercent) {
        this(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, status, createdAt, updatedAt, imagePath,
                imagePublic, isPrimary, batteryDegradationPercent, false, false);
    }

    public Car(UUID id, UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw,
            LocalDate registrationDate, LocalDate deregistrationDate, CarStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt, String imagePath, boolean imagePublic,
            boolean isPrimary, BigDecimal batteryDegradationPercent, boolean isBusinessCar) {
        this(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, status, createdAt, updatedAt, imagePath,
                imagePublic, isPrimary, batteryDegradationPercent, isBusinessCar, false);
    }

    public Car(UUID id, UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw,
            LocalDate registrationDate, LocalDate deregistrationDate, CarStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt, String imagePath, boolean imagePublic,
            boolean isPrimary, BigDecimal batteryDegradationPercent, boolean isBusinessCar, boolean hasHeatPump) {
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
        this.imagePath = imagePath;
        this.imagePublic = imagePublic;
        this.isPrimary = isPrimary;
        this.batteryDegradationPercent = batteryDegradationPercent;
        this.isBusinessCar = isBusinessCar;
        this.hasHeatPump = hasHeatPump;
    }

    public static Car createNew(UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw,
            BigDecimal batteryDegradationPercent) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate registrationDate = LocalDate.of(year, 1, 1);
        return new Car(UUID.randomUUID(), userId, model, year, licensePlate, trim,
                batteryCapacityKwh, powerKw, registrationDate, null, CarStatus.ACTIVE, now, now,
                null, false, false, batteryDegradationPercent, false, false);
    }

    public Car deregister(LocalDate deregistrationDate) {
        return new Car(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, CarStatus.INACTIVE, createdAt, LocalDateTime.now(),
                imagePath, imagePublic, isPrimary, batteryDegradationPercent, isBusinessCar, hasHeatPump);
    }

    public Car withImage(String imagePath, boolean imagePublic) {
        return new Car(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, status, createdAt, LocalDateTime.now(),
                imagePath, imagePublic, isPrimary, batteryDegradationPercent, isBusinessCar, hasHeatPump);
    }

    public Car activate() {
        return new Car(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, status, createdAt, LocalDateTime.now(),
                imagePath, imagePublic, true, batteryDegradationPercent, isBusinessCar, hasHeatPump);
    }

    public Car deactivate() {
        return new Car(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, status, createdAt, LocalDateTime.now(),
                imagePath, imagePublic, false, batteryDegradationPercent, isBusinessCar, hasHeatPump);
    }

    public Car withBusinessCar(boolean businessCar) {
        return new Car(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, status, createdAt, LocalDateTime.now(),
                imagePath, imagePublic, isPrimary, batteryDegradationPercent, businessCar, hasHeatPump);
    }

    public Car withHeatPump(boolean heatPump) {
        return new Car(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                registrationDate, deregistrationDate, status, createdAt, LocalDateTime.now(),
                imagePath, imagePublic, isPrimary, batteryDegradationPercent, isBusinessCar, heatPump);
    }

    public BigDecimal getEffectiveBatteryCapacityKwh() {
        if (batteryCapacityKwh == null) return null;
        if (batteryDegradationPercent == null || batteryDegradationPercent.compareTo(BigDecimal.ZERO) == 0) {
            return batteryCapacityKwh;
        }
        BigDecimal factor = BigDecimal.ONE.subtract(
                batteryDegradationPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return batteryCapacityKwh.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Gibt die effektive Batteriekapazität zum angegebenen Datum zurück, basierend auf dem
     * SoH-Verlauf. Logs vor dem ersten SoH-Eintrag werden mit 100% SoH berechnet.
     * Wenn keine History vorhanden: Fallback auf aktuellen batteryDegradationPercent-Wert.
     */
    public BigDecimal getEffectiveBatteryCapacityKwhAt(LocalDate date, List<BatterySohEntry> sohHistory) {
        if (batteryCapacityKwh == null) return null;
        if (sohHistory == null || sohHistory.isEmpty()) {
            return getEffectiveBatteryCapacityKwh();
        }

        Optional<BatterySohEntry> entry = sohHistory.stream()
                .filter(e -> !e.getRecordedAt().isAfter(date))
                .max(Comparator.comparing(BatterySohEntry::getRecordedAt));

        if (entry.isEmpty()) {
            // Vor dem ersten Messpunkt: Batterie war noch unverbraucht
            return batteryCapacityKwh;
        }

        return batteryCapacityKwh
                .multiply(entry.get().getSohPercent().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public CarBrand.CarModel getModel() { return model; }
    public Integer getYear() { return year; }
    public String getLicensePlate() { return licensePlate; }
    public String getTrim() { return trim; }
    public BigDecimal getBatteryCapacityKwh() { return batteryCapacityKwh; }
    public BigDecimal getPowerKw() { return powerKw; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public LocalDate getDeregistrationDate() { return deregistrationDate; }
    public CarStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getImagePath() { return imagePath; }
    public boolean isImagePublic() { return imagePublic; }
    public boolean isPrimary() { return isPrimary; }
    public BigDecimal getBatteryDegradationPercent() { return batteryDegradationPercent; }
    public boolean isBusinessCar() { return isBusinessCar; }
    public boolean isHeatPump() { return hasHeatPump; }
}
