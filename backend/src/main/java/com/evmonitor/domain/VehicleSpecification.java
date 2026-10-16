package com.evmonitor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
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
    private final String variantName;
    private final BigDecimal netBatteryCapacityKwh;
    private final LocalDate availableFrom;
    private final LocalDate availableTo;
    private final BigDecimal chargingEfficiencyDc;
    private final BigDecimal chargingEfficiencyAc;
    /** Trim-Linie zur Unterscheidung von Varianten (Pflicht für neue Zeilen im Admin-Tab). */
    private final String trimLevel;
    /** Beleg der Werte, https, Pflicht für neue Zeilen im Admin-Tab. */
    private final String sourceUrl;
    /** Maximale DC-Ladeleistung in kW (EVDB "Charge Power (max)"), nur Datenblatt. */
    private final BigDecimal dcMaxPowerKw;
    /** Mittlere DC-Ladeleistung 10-80 % in kW (EVDB "Charge Power (10-80%)"), Basis der Reisezeit. */
    private final BigDecimal dcAvgPower1080Kw;

    public static VehicleSpecification createNew(String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                                  BigDecimal netBatteryCapacityKwh,
                                                  BigDecimal rangeKm, BigDecimal consumptionKwhPer100km,
                                                  WltpType cycleType, RatingSource ratingSource, String variantName) {
        LocalDateTime now = LocalDateTime.now();
        return VehicleSpecification.builder()
                .id(UUID.randomUUID()).carBrand(carBrand).carModel(carModel)
                .batteryCapacityKwh(batteryCapacityKwh).netBatteryCapacityKwh(netBatteryCapacityKwh)
                .officialRangeKm(rangeKm).officialConsumptionKwhPer100km(consumptionKwhPer100km)
                .wltpType(cycleType).ratingSource(ratingSource).createdAt(now).updatedAt(now)
                .variantName(variantName != null ? variantName : "")
                .build();
    }

    /** Backward-compat factory without variantName (defaults to empty string). */
    public static VehicleSpecification createNew(String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                                  BigDecimal netBatteryCapacityKwh,
                                                  BigDecimal rangeKm, BigDecimal consumptionKwhPer100km,
                                                  WltpType cycleType, RatingSource ratingSource) {
        return createNew(carBrand, carModel, batteryCapacityKwh, netBatteryCapacityKwh, rangeKm, consumptionKwhPer100km, cycleType, ratingSource, "");
    }

    /** Backward-compat factory without net capacity. */
    public static VehicleSpecification createNew(String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                                  BigDecimal rangeKm, BigDecimal consumptionKwhPer100km,
                                                  WltpType cycleType, RatingSource ratingSource) {
        return createNew(carBrand, carModel, batteryCapacityKwh, null, rangeKm, consumptionKwhPer100km, cycleType, ratingSource, "");
    }

    /** Backward-compat factory that defaults to WLTP. */
    public static VehicleSpecification createNew(String carBrand, String carModel, BigDecimal batteryCapacityKwh,
                                                  BigDecimal rangeKm, BigDecimal consumptionKwhPer100km,
                                                  WltpType cycleType) {
        return createNew(carBrand, carModel, batteryCapacityKwh, null, rangeKm, consumptionKwhPer100km, cycleType, RatingSource.WLTP, "");
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
