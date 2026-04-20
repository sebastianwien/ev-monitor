package com.evmonitor.application;

/**
 * Describes how reliably a consumption value was calculated.
 *
 * KWH_PRIMARY   — kWh-primary formula: effectiveKwh + SoC-level correction between sessions.
 *                 Most accurate. Direct energy measurement drives the result.
 * SOC_DELTA     — Pure SoC-delta fallback: (socAfter_X - socBefore_Y) * capacity / 100.
 *                 Used only when kwhCharged is absent. Sensitive to SoH errors.
 * KWH_ESTIMATED — kWh / distance only, no SoC data available.
 *                 Least accurate: does not account for partial charging between sessions.
 */
public enum CalculationQuality {
    KWH_PRIMARY,
    SOC_DELTA,
    KWH_ESTIMATED
}
