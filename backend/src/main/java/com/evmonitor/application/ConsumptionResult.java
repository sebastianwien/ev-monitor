package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Result of a per-trip consumption calculation, including a plausibility verdict.
 *
 * @param value       consumption in kWh/100km
 * @param plausible   false if the value was flagged as an outlier by the plausibility check
 * @param distanceKm  trip distance in km (logX odometer → logY odometer)
 * @param quality     how the value was calculated — affects display precision in the UI
 */
public record ConsumptionResult(BigDecimal value, boolean plausible, int distanceKm, CalculationQuality quality) {

    /** Convenience constructor for kWh-primary results (most accurate). */
    public ConsumptionResult(BigDecimal value, boolean plausible, int distanceKm) {
        this(value, plausible, distanceKm, CalculationQuality.KWH_PRIMARY);
    }

    /** Backwards-compat: true when quality is KWH_ESTIMATED (kWh/distance fallback, no SoC). */
    public boolean estimated() {
        return quality == CalculationQuality.KWH_ESTIMATED;
    }
}
