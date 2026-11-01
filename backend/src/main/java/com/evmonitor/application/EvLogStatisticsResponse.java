package com.evmonitor.application;

import com.evmonitor.application.range.RangeEstimateService;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EvLogStatisticsResponse(
        // Key Metrics
        BigDecimal totalKwhCharged,
        BigDecimal energyCostEur,
        BigDecimal fixedCostEur,      // Fixkosten brutto, ohne Einnahmen
        BigDecimal fixedIncomeEur,    // Einnahmen aus Fixkosten-Kategorien, positiv ausgewiesen
        BigDecimal totalCostEur,      // energyCost + fixedCost - fixedIncome
        BigDecimal avgCostPerKwh,
        BigDecimal cheapestChargeEur,
        BigDecimal mostExpensiveChargeEur,
        Integer avgChargeDurationMinutes,
        Integer totalCharges,
        BigDecimal totalDistanceKm,            // null if no odometer data
        BigDecimal avgConsumptionKwhPer100km,  // null if no odometer data
        Integer estimatedConsumptionCount,     // count of logs where consumption is estimated (kWh/distance fallback)

        // Seasonal consumption (null if insufficient data for that season)
        BigDecimal summerConsumptionKwhPer100km,
        BigDecimal winterConsumptionKwhPer100km,

        // Charge Over Time (for chart)
        List<ChargeDataPoint> chargesOverTime,

        // Charging type split (AC/DC/UNKNOWN kWh)
        ChargingTypeSplit chargingTypeSplit,

        // Location split (public/private kWh)
        LocationSplit locationSplit,

        // Peer benchmark - null if no vehicle spec linked or zero peer cars
        PeerBenchmark peerBenchmark,

        // Charging efficiency - only for logs with both kwhCharged and kwhAtVehicle
        ChargingEfficiencySplit chargingEfficiencySplit,

        // Ladung hinter mostExpensiveChargeEur, damit die Story genau sie öffnet; null ohne Ladung mit Preis
        UUID mostExpensiveChargeLogId,

        // Fahrverbrauch aus den Fahrten (DriveConsumptionService), flach im JSON: driveConsumptionKwhPer100km,
        // driveSummer/WinterConsumptionKwhPer100km, modelDriveConsumptionKwhPer100km, modelDriveUsers, driveMonths.
        // Null ohne Berechnung; die Felder fehlen dann im JSON.
        @JsonUnwrapped DriveStatistics drive,

        // Reichweite je Zeitraum (current, summer, winter) nach den vier Stufen der RangeConsumptionPolicy
        // (RangeEstimateService); null ohne Berechnung.
        RangeEstimateService.RangeStatistics range,

        // CO₂ gegen einen Benziner der Fahrzeugklasse (Co2Calculator); null ohne vollständigen Tacho-Abschnitt.
        Co2Statistics co2
) {
    /** Ohne Fahrverbrauch; {@link #withDrive} ergänzt ihn. */
    public EvLogStatisticsResponse(BigDecimal totalKwhCharged, BigDecimal energyCostEur, BigDecimal fixedCostEur,
            BigDecimal fixedIncomeEur, BigDecimal totalCostEur, BigDecimal avgCostPerKwh, BigDecimal cheapestChargeEur,
            BigDecimal mostExpensiveChargeEur, Integer avgChargeDurationMinutes, Integer totalCharges,
            BigDecimal totalDistanceKm, BigDecimal avgConsumptionKwhPer100km, Integer estimatedConsumptionCount,
            BigDecimal summerConsumptionKwhPer100km, BigDecimal winterConsumptionKwhPer100km,
            List<ChargeDataPoint> chargesOverTime, ChargingTypeSplit chargingTypeSplit, LocationSplit locationSplit,
            PeerBenchmark peerBenchmark, ChargingEfficiencySplit chargingEfficiencySplit, UUID mostExpensiveChargeLogId) {
        this(totalKwhCharged, energyCostEur, fixedCostEur, fixedIncomeEur, totalCostEur, avgCostPerKwh, cheapestChargeEur,
                mostExpensiveChargeEur, avgChargeDurationMinutes, totalCharges, totalDistanceKm, avgConsumptionKwhPer100km,
                estimatedConsumptionCount, summerConsumptionKwhPer100km, winterConsumptionKwhPer100km, chargesOverTime,
                chargingTypeSplit, locationSplit, peerBenchmark, chargingEfficiencySplit, mostExpensiveChargeLogId, null, null, null);
    }

    public EvLogStatisticsResponse withDrive(DriveStatistics drive) {
        return new EvLogStatisticsResponse(totalKwhCharged, energyCostEur, fixedCostEur, fixedIncomeEur, totalCostEur,
                avgCostPerKwh, cheapestChargeEur, mostExpensiveChargeEur, avgChargeDurationMinutes, totalCharges,
                totalDistanceKm, avgConsumptionKwhPer100km, estimatedConsumptionCount, summerConsumptionKwhPer100km,
                winterConsumptionKwhPer100km, chargesOverTime, chargingTypeSplit, locationSplit, peerBenchmark,
                chargingEfficiencySplit, mostExpensiveChargeLogId, drive, range, co2);
    }

    public EvLogStatisticsResponse withRange(RangeEstimateService.RangeStatistics range) {
        return new EvLogStatisticsResponse(totalKwhCharged, energyCostEur, fixedCostEur, fixedIncomeEur, totalCostEur,
                avgCostPerKwh, cheapestChargeEur, mostExpensiveChargeEur, avgChargeDurationMinutes, totalCharges,
                totalDistanceKm, avgConsumptionKwhPer100km, estimatedConsumptionCount, summerConsumptionKwhPer100km,
                winterConsumptionKwhPer100km, chargesOverTime, chargingTypeSplit, locationSplit, peerBenchmark,
                chargingEfficiencySplit, mostExpensiveChargeLogId, drive, range, co2);
    }

    public EvLogStatisticsResponse withCo2(Co2Statistics co2) {
        return new EvLogStatisticsResponse(totalKwhCharged, energyCostEur, fixedCostEur, fixedIncomeEur, totalCostEur,
                avgCostPerKwh, cheapestChargeEur, mostExpensiveChargeEur, avgChargeDurationMinutes, totalCharges,
                totalDistanceKm, avgConsumptionKwhPer100km, estimatedConsumptionCount, summerConsumptionKwhPer100km,
                winterConsumptionKwhPer100km, chargesOverTime, chargingTypeSplit, locationSplit, peerBenchmark,
                chargingEfficiencySplit, mostExpensiveChargeLogId, drive, range, co2);
    }

    public record ChargeDataPoint(
            LocalDateTime timestamp,
            BigDecimal costEur,                // null if no log in this period had cost_eur set; 0 means "really 0 €" (e.g. solar)
            BigDecimal kwhCharged,             // grid-side kWh; falls back to kwh_at_vehicle/efficiency for telemetry-only logs
            BigDecimal distanceKm,             // 0 if logs exist but no plausible distance (e.g. home-only charging)
            BigDecimal consumptionKwhPer100km, // null if distance is 0 or no odometer data
            int charges,                       // logs in this period
            BigDecimal co2SavedKg              // CO₂ der Tacho-Abschnitte, die in dieser Periode schließen; null ohne
    ) {}

    /**
     * CO₂-Bilanz des Zeitraums aus den Tacho-Abschnitten (Co2Calculator), vorzeichenrichtig.
     *
     * @param savedKg              Benziner minus Strom, negativ bei mehr CO₂ als der Benziner
     * @param petrolKg             CO₂ eines Benziners der Fahrzeugklasse auf distanceKm
     * @param electricityKg        CO₂ des geladenen Netzstroms
     * @param distanceKm           Strecke der Rechnung (nur vollständige Abschnitte)
     * @param petrolLiters         Liter, die dieser Benziner getankt hätte
     * @param petrolLitersPer100km Realverbrauch des Benziners der Klasse
     */
    public record Co2Statistics(
            BigDecimal savedKg,
            BigDecimal petrolKg,
            BigDecimal electricityKg,
            int distanceKm,
            BigDecimal petrolLiters,
            BigDecimal petrolLitersPer100km
    ) {
        /** Null ohne Bilanz. */
        public static Co2Statistics of(com.evmonitor.domain.Co2Balance b, BigDecimal petrolLitersPer100km) {
            if (b == null) return null;
            return new Co2Statistics(kg(b.savedKg()), kg(b.petrolKg()), kg(b.electricityKg()), b.distanceKm(),
                    kg(b.petrolLiters()), petrolLitersPer100km);
        }

        static BigDecimal kg(double value) {
            return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * Peer comparison for the user's own stats in the selected period vs. anonymized community lifetime avg.
     * Match is first attempted by vehicleSpecificationId (exact variant), with fallback to
     * car model (all variants) when no spec-level peers exist.
     *
     * @param userPeriodConsumptionKwhPer100km    distance-weighted avg for this user within the selected period (lifetime when no period is set)
     * @param peerAvgConsumptionKwhPer100km       community avg across all non-seed peer cars
     * @param userPeriodCostPerKwh                user's own avg cost/kWh within the selected period (lifetime when no period is set, null if no cost data)
     * @param peerAvgCostPerKwh                   community lifetime avg cost/kWh across all non-seed peers, worldwide (null if no peer has cost data)
     * @param uniquePeerUsers                     distinct user count among peer cars
     * @param peerTripCount                       plausible trip count used for consumption avg
     * @param peerLogCount                        peer charging logs included in statistics - the data basis behind the averages
     * @param matchType                           SPEC = same vehicleSpecificationId, MODEL = fallback to same car model (all variants)
     */
    public record ChargingTypeSplit(
            BigDecimal acKwh,
            BigDecimal dcKwh,
            BigDecimal unknownKwh,
            /** Euro je kWh aus den AC-Ladungen mit Preis; null ohne bepreiste AC-Ladung. */
            BigDecimal acEurPerKwh,
            /** Euro je kWh aus den DC-Ladungen mit Preis; null ohne bepreiste DC-Ladung. */
            BigDecimal dcEurPerKwh
    ) {}

    public record LocationSplit(
            BigDecimal publicKwh,
            BigDecimal privateKwh,
            BigDecimal unknownKwh
    ) {}

    public record ChargingEfficiencySplit(
            BigDecimal gridKwh,
            BigDecimal vehicleKwh,
            int coveredLogCount,
            int totalLogCount,
            /** Ladeverlust aller Ladungen: gemessen (Netz minus Akku) oder mit dem Wirkungsgrad hochgerechnet. */
            BigDecimal lossKwh,
            /** Ladungen, deren Verlust hochgerechnet statt gemessen ist. */
            int estimatedLossLogCount
    ) {}

    public record PeerBenchmark(
            BigDecimal userPeriodConsumptionKwhPer100km,
            BigDecimal peerAvgConsumptionKwhPer100km,
            BigDecimal userPeriodCostPerKwh,
            BigDecimal peerAvgCostPerKwh,
            int uniquePeerUsers,
            int peerTripCount,
            int peerLogCount,
            MatchType matchType,
            boolean peerConsumptionLifetime,
            boolean peerCostLifetime
    ) {
        public enum MatchType { SPEC, MODEL }
    }
}
