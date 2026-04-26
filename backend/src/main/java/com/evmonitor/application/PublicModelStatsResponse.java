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
        int uniqueCars,
        BigDecimal avgCostPerKwh,
        BigDecimal acAvgCostPerKwh,
        BigDecimal dcAvgCostPerKwh,
        BigDecimal avgKwhPerSession,
        BigDecimal avgConsumptionKwhPer100km,
        int estimatedConsumptionCount,
        BigDecimal avgChargingPowerKw,

        List<WltpVariant> wltpVariants,
        List<EpaVariant> epaVariants,
        SeasonalDistribution seasonalDistribution,
        List<YearEntry> yearDistribution,
        List<RouteTypeEntry> routeTypeDistribution
) {
    public record WltpVariant(
            BigDecimal batteryCapacityKwh,
            String variantName,
            String displayLabel,
            BigDecimal wltpRangeKm,             // best (max) range across group
            BigDecimal wltpRangeMinKm,           // null if single-spec or all same
            BigDecimal wltpConsumptionKwhPer100km,    // average across group (fair delta baseline)
            BigDecimal wltpConsumptionMinKwhPer100km, // null if single-spec
            BigDecimal wltpConsumptionMaxKwhPer100km,
            BigDecimal realConsumptionKwhPer100km,
            BigDecimal realConsumptionMinKwhPer100km,
            BigDecimal realConsumptionMaxKwhPer100km,
            Integer realConsumptionTripCount,
            Integer estimatedConsumptionCount,
            SeasonalDistribution seasonalDistribution
    ) {}

    public record EpaVariant(
            BigDecimal batteryCapacityKwh,
            String variantName,
            String displayLabel,
            BigDecimal epaRangeKm,
            BigDecimal epaConsumptionKwhPer100km,
            BigDecimal epaConsumptionMinKwhPer100km,
            BigDecimal epaConsumptionMaxKwhPer100km,
            BigDecimal realConsumptionKwhPer100km,
            BigDecimal realConsumptionMinKwhPer100km,
            BigDecimal realConsumptionMaxKwhPer100km,
            Integer realConsumptionTripCount,
            Integer estimatedConsumptionCount,
            SeasonalDistribution seasonalDistribution
    ) {}

    public record YearEntry(int year, int carCount) {}

    public record RouteTypeEntry(String routeType, int count) {}

    public record SeasonalDistribution(
            int summerPercentage,
            int winterPercentage,
            BigDecimal summerConsumptionKwhPer100km,
            BigDecimal winterConsumptionKwhPer100km,
            BigDecimal totalConsumptionKwhPer100km,
            int summerLogCount,
            int winterLogCount
    ) {}
}
