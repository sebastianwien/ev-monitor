package com.evmonitor.application;

import java.math.BigDecimal;

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
    static RouteTypeConsumptionResult empty() {
        return new RouteTypeConsumptionResult(null, null, null, 0, 0, 0, 0);
    }
}
