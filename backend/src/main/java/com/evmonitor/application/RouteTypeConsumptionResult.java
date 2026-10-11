package com.evmonitor.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Community consumption per route type (CITY, COMBINED, HIGHWAY) of the log that closes a trip.
 * Consumptions in kWh/100km, null if the type has no plausible trip. Counts are plausible trips,
 * highwayCarCount the distinct cars behind the highway trips.
 */
public record RouteTypeConsumptionResult(
        BigDecimal cityKwhPer100km,
        BigDecimal combinedKwhPer100km,
        BigDecimal highwayKwhPer100km,
        int cityLogCount,
        int combinedLogCount,
        int highwayLogCount,
        int highwayCarCount
) {
    /**
     * Mean over all route-typed trips, each type weighted by its trips; null without any. The
     * comparison base for the highway value: same trips, same period, same season mix, unlike the
     * model's overall mean, which reaches back to logs from before route types were recorded.
     */
    public BigDecimal typedMeanKwhPer100km() {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        if (cityKwhPer100km != null && cityLogCount > 0) { sum = sum.add(cityKwhPer100km.multiply(BigDecimal.valueOf(cityLogCount))); count += cityLogCount; }
        if (combinedKwhPer100km != null && combinedLogCount > 0) { sum = sum.add(combinedKwhPer100km.multiply(BigDecimal.valueOf(combinedLogCount))); count += combinedLogCount; }
        if (highwayKwhPer100km != null && highwayLogCount > 0) { sum = sum.add(highwayKwhPer100km.multiply(BigDecimal.valueOf(highwayLogCount))); count += highwayLogCount; }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    static RouteTypeConsumptionResult empty() {
        return new RouteTypeConsumptionResult(null, null, null, 0, 0, 0, 0);
    }
}
