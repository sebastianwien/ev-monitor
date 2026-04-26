package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EvLogStatisticsResponse(
        // Key Metrics
        BigDecimal totalKwhCharged,
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

        // Peer benchmark — null if no vehicle spec linked or zero peer cars
        PeerBenchmark peerBenchmark
) {
    public record ChargeDataPoint(
            LocalDateTime timestamp,
            BigDecimal costEur,
            BigDecimal kwhCharged,
            BigDecimal distanceKm,             // null if no odometer data
            BigDecimal consumptionKwhPer100km  // null if no odometer data
    ) {}

    /**
     * Peer comparison for the user's own lifetime stats vs. anonymized community avg
     * for cars with the same vehicle_specification_id.
     *
     * @param userLifetimeConsumptionKwhPer100km  distance-weighted lifetime avg for this user (no time filter)
     * @param peerAvgConsumptionKwhPer100km       community avg across all non-seed peer cars with same spec
     * @param userLifetimeCostPerKwh              user's own lifetime avg cost/kWh (null if no cost data)
     * @param peerAvgCostPerKwh                   community avg cost/kWh — same-country peers only (null if < 3 same-country peers)
     * @param uniquePeerUsers                     distinct user count among peer cars
     * @param peerTripCount                       plausible trip count used for consumption avg
     * @param sameCountryPeerUsers                distinct user count with same country (for cost comparison)
     * @param userCountry                         ISO country code of current user (for UI label)
     * @param sufficientData                      true if >= 3 unique peers and >= 10 trips
     */
    public record PeerBenchmark(
            BigDecimal userLifetimeConsumptionKwhPer100km,
            BigDecimal peerAvgConsumptionKwhPer100km,
            BigDecimal userLifetimeCostPerKwh,
            BigDecimal peerAvgCostPerKwh,
            int uniquePeerUsers,
            int peerTripCount,
            int sameCountryPeerUsers,
            String userCountry,
            boolean sufficientData
    ) {}
}
