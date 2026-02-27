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
        List<WltpVariant> wltpVariants
) {
    public record WltpVariant(
            BigDecimal batteryCapacityKwh,
            BigDecimal wltpRangeKm,
            BigDecimal wltpConsumptionKwhPer100km
    ) {}
}
