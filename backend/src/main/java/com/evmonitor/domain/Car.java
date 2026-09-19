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
    /** Frist, in der ein gelöschtes Fahrzeug wiederhergestellt werden kann. */
    public static final int RESTORE_WINDOW_DAYS = 7;

    private final UUID id;
    private final UUID userId;
    private final CarBrand.CarModel model;
    private final Integer year;
    private final String licensePlate;
    private final String trim;
    /**
     * User-supplied net (usable) battery capacity. Only used when no
     * vehicleSpecification is linked, otherwise specNetBatteryCapacityKwh wins.
     */
    private final BigDecimal customNetCapacityKwh;
    private final UUID vehicleSpecificationId;
    private final BigDecimal specNetBatteryCapacityKwh;
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
    /** DSGVO: gesetzt, sobald der Besitzer sein Konto gelöscht hat - userId ist dann NULL. */
    private final LocalDateTime anonymizedAt;
    /**
     * Soft-Delete: gesetzt, wenn der User das Fahrzeug gelöscht hat. Die Zeile und alle
     * abhängigen Daten bleiben {@link #RESTORE_WINDOW_DAYS} Tage bestehen, danach räumt
     * der Purge-Job hart ab und die FK-Kaskade greift wie zuvor.
     */
    private final LocalDateTime deletedAt;

    public static Car createNew(UUID userId, CarBrand.CarModel model, Integer year, String licensePlate,
            String trim, BigDecimal customNetCapacityKwh, BigDecimal powerKw,
            BigDecimal batteryDegradationPercent) {
        LocalDateTime now = LocalDateTime.now();
        return Car.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .model(model)
                .year(year)
                .licensePlate(licensePlate)
                .trim(trim)
                .customNetCapacityKwh(customNetCapacityKwh)
                .powerKw(powerKw)
                .registrationDate(LocalDate.of(year, 1, 1))
                .status(CarStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .batteryDegradationPercent(batteryDegradationPercent)
                .build();
    }

    /** Einziger erlaubter Ownership-Check: ein anonymisiertes Auto (userId NULL) gehört niemandem. */
    public boolean isOwnedBy(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Nur innerhalb des Fensters darf der User selbst wiederherstellen. */
    public boolean isRestorable() {
        return deletedAt != null && deletedAt.isAfter(LocalDateTime.now().minusDays(RESTORE_WINDOW_DAYS));
    }

    /** Soft-Delete: Fahrzeug verschwindet aus allen Ansichten, Daten bleiben erhalten. */
    public Car softDelete() {
        return toBuilder().deletedAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    /**
     * Macht den Soft-Delete rückgängig. {@code keepPrimary} ist false, wenn der User
     * inzwischen ein anderes Hauptfahrzeug hat - sonst gäbe es zwei davon.
     */
    public Car restore(boolean keepPrimary) {
        return toBuilder().deletedAt(null).primary(primary && keepPrimary)
                .updatedAt(LocalDateTime.now()).build();
    }

    /** DSGVO-Kontolöschung: Personenbezug kappen, Spec/Baujahr/Kapazität für die Community-Statistik behalten. */
    public Car anonymize() {
        return toBuilder().userId(null).licensePlate(null).imagePath(null).imagePublic(false)
                .anonymizedAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
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

    /**
     * True if this car is a Tesla. Drives the marken-aware entitlement gates:
     * Tesla cars get free Fleet-Telemetry data plus the live-charging card, while
     * other brands stay on the paid AutoSync model.
     */
    public boolean isTesla() {
        return model != null && model.getBrand() == CarBrand.TESLA;
    }

    /**
     * Nominale Netto-Kapazitaet (vor SoH-Adjustierung). Single Source of Truth fuer
     * "wieviel kWh netto hat dieses Auto laut Stammdaten". Vorrang: specNet, sonst customNet.
     */
    public BigDecimal getNominalNetCapacityKwh() {
        return specNetBatteryCapacityKwh != null ? specNetBatteryCapacityKwh : customNetCapacityKwh;
    }

    public BigDecimal getEffectiveBatteryCapacityKwh() {
        BigDecimal base = getNominalNetCapacityKwh();
        if (base == null) return null;
        if (batteryDegradationPercent == null || batteryDegradationPercent.compareTo(BigDecimal.ZERO) == 0) {
            return base;
        }
        BigDecimal factor = BigDecimal.ONE.subtract(
                batteryDegradationPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return base.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Gibt die effektive Batteriekapazität zum angegebenen Datum zurück, basierend auf dem
     * SoH-Verlauf. Logs vor dem ersten SoH-Eintrag werden mit 100% SoH berechnet.
     * Wenn keine History vorhanden: Fallback auf aktuellen batteryDegradationPercent-Wert.
     */
    public BigDecimal getEffectiveBatteryCapacityKwhAt(LocalDate date, List<BatterySohEntry> sohHistory) {
        BigDecimal base = getNominalNetCapacityKwh();
        if (base == null) return null;
        if (sohHistory == null || sohHistory.isEmpty()) {
            return getEffectiveBatteryCapacityKwh();
        }

        Optional<BatterySohEntry> entry = sohHistory.stream()
                .filter(e -> !e.getRecordedAt().isAfter(date))
                .max(Comparator.comparing(BatterySohEntry::getRecordedAt));

        if (entry.isEmpty()) {
            return base;
        }

        return base
                .multiply(entry.get().getSohPercent().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
