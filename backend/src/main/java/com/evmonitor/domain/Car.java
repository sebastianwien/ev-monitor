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

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id).userId(this.userId).model(this.model).year(this.year)
                .licensePlate(this.licensePlate).trim(this.trim)
                .batteryCapacityKwh(this.batteryCapacityKwh).powerKw(this.powerKw)
                .registrationDate(this.registrationDate).deregistrationDate(this.deregistrationDate)
                .status(this.status).createdAt(this.createdAt).updatedAt(this.updatedAt)
                .imagePath(this.imagePath).imagePublic(this.imagePublic).isPrimary(this.isPrimary)
                .batteryDegradationPercent(this.batteryDegradationPercent)
                .isBusinessCar(this.isBusinessCar).hasHeatPump(this.hasHeatPump);
    }

    public static class Builder {
        private UUID id;
        private UUID userId;
        private CarBrand.CarModel model;
        private Integer year;
        private String licensePlate;
        private String trim;
        private BigDecimal batteryCapacityKwh;
        private BigDecimal powerKw;
        private LocalDate registrationDate;
        private LocalDate deregistrationDate;
        private CarStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String imagePath;
        private boolean imagePublic;
        private boolean isPrimary;
        private BigDecimal batteryDegradationPercent;
        private boolean isBusinessCar;
        private boolean hasHeatPump;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder model(CarBrand.CarModel model) { this.model = model; return this; }
        public Builder year(Integer year) { this.year = year; return this; }
        public Builder licensePlate(String licensePlate) { this.licensePlate = licensePlate; return this; }
        public Builder trim(String trim) { this.trim = trim; return this; }
        public Builder batteryCapacityKwh(BigDecimal v) { this.batteryCapacityKwh = v; return this; }
        public Builder powerKw(BigDecimal v) { this.powerKw = v; return this; }
        public Builder registrationDate(LocalDate v) { this.registrationDate = v; return this; }
        public Builder deregistrationDate(LocalDate v) { this.deregistrationDate = v; return this; }
        public Builder status(CarStatus status) { this.status = status; return this; }
        public Builder createdAt(LocalDateTime v) { this.createdAt = v; return this; }
        public Builder updatedAt(LocalDateTime v) { this.updatedAt = v; return this; }
        public Builder imagePath(String imagePath) { this.imagePath = imagePath; return this; }
        public Builder imagePublic(boolean v) { this.imagePublic = v; return this; }
        public Builder isPrimary(boolean v) { this.isPrimary = v; return this; }
        public Builder batteryDegradationPercent(BigDecimal v) { this.batteryDegradationPercent = v; return this; }
        public Builder isBusinessCar(boolean v) { this.isBusinessCar = v; return this; }
        public Builder hasHeatPump(boolean v) { this.hasHeatPump = v; return this; }

        public Car build() {
            return new Car(id, userId, model, year, licensePlate, trim, batteryCapacityKwh, powerKw,
                    registrationDate, deregistrationDate, status, createdAt, updatedAt, imagePath,
                    imagePublic, isPrimary, batteryDegradationPercent, isBusinessCar, hasHeatPump);
        }
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
        return toBuilder().deregistrationDate(deregistrationDate).status(CarStatus.INACTIVE)
                .updatedAt(LocalDateTime.now()).build();
    }

    public Car withImage(String imagePath, boolean imagePublic) {
        return toBuilder().imagePath(imagePath).imagePublic(imagePublic).updatedAt(LocalDateTime.now()).build();
    }

    public Car activate() {
        return toBuilder().isPrimary(true).updatedAt(LocalDateTime.now()).build();
    }

    public Car deactivate() {
        return toBuilder().isPrimary(false).updatedAt(LocalDateTime.now()).build();
    }

    public Car withBusinessCar(boolean businessCar) {
        return toBuilder().isBusinessCar(businessCar).updatedAt(LocalDateTime.now()).build();
    }

    public Car withHeatPump(boolean heatPump) {
        return toBuilder().hasHeatPump(heatPump).updatedAt(LocalDateTime.now()).build();
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
