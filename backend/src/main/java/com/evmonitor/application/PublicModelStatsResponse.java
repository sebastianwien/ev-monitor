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
        String modelDisplayName,

        // Community data
        int logCount,
        int uniqueContributors,
        BigDecimal avgCostPerKwh,
        BigDecimal avgKwhPerSession,
        BigDecimal avgConsumptionKwhPer100km, // null if no odometer data

        // WLTP official data (all known variants for this model)
        List<WltpVariant> wltpVariants,

        // Seasonal data distribution (null if no odometer data)
        SeasonalDistribution seasonalDistribution
) {
    public record WltpVariant(
            BigDecimal batteryCapacityKwh,
            BigDecimal wltpRangeKm,
            BigDecimal wltpConsumptionKwhPer100km
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
            int summerLogCount,        // number of logs in summer
            int winterLogCount         // number of logs in winter
    ) {}
}
