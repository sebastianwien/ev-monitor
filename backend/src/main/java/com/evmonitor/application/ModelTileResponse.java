package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Slim model summary for the embeddable partner tile (ev-monitor-tile.js).
 * hasData=false is the fallback for unknown slugs and models without measured consumption;
 * the tile then asks the reader to contribute. Ranges are 90 % of the net battery divided by
 * the real consumption, rounded to 10 km (same rule as the model page hero), null when the
 * underlying value is missing. Seasonal ranges and seasonal consumption need at least 10 logs per season.
 * Charge costs are at the plug, charging losses included: the average charge of the model and a
 * fast-charging stop from 10 to 80 % (FastChargeStop) on the battery the ranges use. Fields after
 * modelUrl are additive, older tile scripts ignore them.
 * Platform-wide figures for the tile's claim: all public logs and trips, and the largest gap between
 * measured and WLTP consumption over the models with at least 100 logs (null when no model clears it
 * or none lies above WLTP).
 */
public record ModelTileResponse(
        boolean hasData,
        String modelDisplayName,
        BigDecimal realConsumptionKwhPer100km,
        Integer typicalRangeKm,
        Integer summerRangeKm,
        Integer winterRangeKm,
        int logCount,
        int uniqueCars,
        String modelUrl,
        BigDecimal summerConsumptionKwhPer100km,
        BigDecimal winterConsumptionKwhPer100km,
        Integer summerLogCount,
        Integer winterLogCount,
        BigDecimal typicalChargeCostEur,
        BigDecimal fastStopCostEur,
        long totalLogCount,
        long totalTripCount,
        Integer wltpGapMaxPercent
) {
    public static ModelTileResponse unknown() {
        return new ModelTileResponse(false, null, null, null, null, null, 0, 0, null, null, null, null, null, null, null, 0, 0, null);
    }
}
