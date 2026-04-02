package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public aggregated statistics for a specific car model.
 * Only includes data from real (non-seed) users — anonymized and aggregated.
 */
public record PublicModelStatsResponse(
        String brand,
        String model,
        String brandDisplayName,
        String modelDisplayName,
        String category,
        String categoryDisplayName,

        // Community data
        int logCount,
        int uniqueContributors,
        BigDecimal avgCostPerKwh,
        BigDecimal acAvgCostPerKwh,   // AC-only (Heimladen/Wallbox), null if <5 sessions
        BigDecimal dcAvgCostPerKwh,   // DC-only (Schnellladen), null if <5 sessions
        BigDecimal avgKwhPerSession,
        BigDecimal avgConsumptionKwhPer100km, // null if no odometer data
        int estimatedConsumptionCount,         // count of trips where consumption is estimated (kWh/distance fallback)
        BigDecimal avgChargingPowerKw,         // null if no sessions with duration data

        // WLTP official data (all known variants for this model)
        List<WltpVariant> wltpVariants,

        // Seasonal data distribution (null if no odometer data)
        SeasonalDistribution seasonalDistribution
) {
    public record WltpVariant(
            BigDecimal batteryCapacityKwh,
            String variantName,                    // nullable — e.g. "Long Range", "Performance", "Pro S"
            BigDecimal wltpRangeKm,
            BigDecimal wltpConsumptionKwhPer100km,
            BigDecimal realConsumptionKwhPer100km, // null if no user data for this battery size
            Integer realConsumptionTripCount,      // number of trips used for realConsumptionKwhPer100km
            SeasonalDistribution seasonalDistribution // null if no seasonal data for this variant
    ) {}

    /**
     * Seasonal breakdown: consumption and distance per season.
     * Summer: Apr-Sep, Winter: Oct-Mar
     */
    public record SeasonalDistribution(
            int summerPercentage,      // % of driven km in summer (0-100)
            int winterPercentage,      // % of driven km in winter (0-100)
            BigDecimal summerConsumptionKwhPer100km,  // avg consumption in summer (nullable)
            BigDecimal winterConsumptionKwhPer100km,  // avg consumption in winter (nullable)
            BigDecimal totalConsumptionKwhPer100km,   // distance-weighted total across both seasons (nullable)
            int summerLogCount,        // number of logs in summer
            int winterLogCount         // number of logs in winter
    ) {}
}
