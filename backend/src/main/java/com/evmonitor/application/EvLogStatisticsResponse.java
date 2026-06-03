package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EvLogStatisticsResponse(
        // Key Metrics
        BigDecimal totalKwhCharged,
        BigDecimal energyCostEur,
        BigDecimal fixedCostEur,
        BigDecimal totalCostEur,
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

        // Peer benchmark — null if no vehicle spec linked or zero peer cars
        PeerBenchmark peerBenchmark,

        // Charging efficiency — only for logs with both kwhCharged and kwhAtVehicle
        ChargingEfficiencySplit chargingEfficiencySplit
) {
    public record ChargeDataPoint(
            LocalDateTime timestamp,
            BigDecimal costEur,                // null if no log in this period had cost_eur set; 0 means "really 0 €" (e.g. solar)
            BigDecimal kwhCharged,             // grid-side kWh; falls back to kwh_at_vehicle/efficiency for telemetry-only logs
            BigDecimal distanceKm,             // 0 if logs exist but no plausible distance (e.g. home-only charging)
            BigDecimal consumptionKwhPer100km  // null if distance is 0 or no odometer data
    ) {}

    /**
     * Peer comparison for the user's own lifetime stats vs. anonymized community avg.
     * Match is first attempted by vehicleSpecificationId (exact variant), with fallback to
     * car model (all variants) when no spec-level peers exist.
     *
     * @param userLifetimeConsumptionKwhPer100km  distance-weighted lifetime avg for this user (no time filter)
     * @param peerAvgConsumptionKwhPer100km       community avg across all non-seed peer cars
     * @param userLifetimeCostPerKwh              user's own lifetime avg cost/kWh (null if no cost data)
     * @param peerAvgCostPerKwh                   community avg cost/kWh - same-country peers only (null if no same-country peers with cost data)
     * @param uniquePeerUsers                     distinct user count among peer cars
     * @param peerTripCount                       plausible trip count used for consumption avg
     * @param sameCountryPeerUsers                distinct user count with same country (for cost comparison)
     * @param userCountry                         ISO country code of current user (for UI label)
     * @param matchType                           SPEC = same vehicleSpecificationId, MODEL = fallback to same car model (all variants)
     */
    public record ChargingTypeSplit(
            BigDecimal acKwh,
            BigDecimal dcKwh,
            BigDecimal unknownKwh
    ) {}

    public record LocationSplit(
            BigDecimal publicKwh,
            BigDecimal privateKwh
    ) {}

    public record ChargingEfficiencySplit(
            BigDecimal gridKwh,
            BigDecimal vehicleKwh,
            int coveredLogCount,
            int totalLogCount
    ) {}

    public record PeerBenchmark(
            BigDecimal userLifetimeConsumptionKwhPer100km,
            BigDecimal peerAvgConsumptionKwhPer100km,
            BigDecimal userLifetimeCostPerKwh,
            BigDecimal peerAvgCostPerKwh,
            int uniquePeerUsers,
            int peerTripCount,
            int sameCountryPeerUsers,
            String userCountry,
            MatchType matchType
    ) {
        public enum MatchType { SPEC, MODEL }
    }
}
