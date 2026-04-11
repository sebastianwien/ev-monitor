package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
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
    private final boolean primary;
    private final BigDecimal batteryDegradationPercent;
    private final boolean businessCar;
    private final boolean heatPump;

    public static Car createNew(UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal batteryCapacityKwh, BigDecimal powerKw,
            BigDecimal batteryDegradationPercent) {
        LocalDateTime now = LocalDateTime.now();
        return Car.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .model(model)
                .year(year)
                .licensePlate(licensePlate)
                .trim(trim)
                .batteryCapacityKwh(batteryCapacityKwh)
                .powerKw(powerKw)
                .registrationDate(LocalDate.of(year, 1, 1))
                .status(CarStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .batteryDegradationPercent(batteryDegradationPercent)
                .build();
    }

    public Car deregister(LocalDate deregistrationDate) {
        return toBuilder().deregistrationDate(deregistrationDate).status(CarStatus.INACTIVE)
                .updatedAt(LocalDateTime.now()).build();
    }

    public Car withImage(String imagePath, boolean imagePublic) {
        return toBuilder().imagePath(imagePath).imagePublic(imagePublic).updatedAt(LocalDateTime.now()).build();
    }

    public Car activate() {
        return toBuilder().primary(true).updatedAt(LocalDateTime.now()).build();
    }

    public Car deactivate() {
        return toBuilder().primary(false).updatedAt(LocalDateTime.now()).build();
    }

    public Car withBusinessCar(boolean businessCar) {
        return toBuilder().businessCar(businessCar).updatedAt(LocalDateTime.now()).build();
    }

    public Car withHeatPump(boolean heatPump) {
        return toBuilder().heatPump(heatPump).updatedAt(LocalDateTime.now()).build();
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
}
