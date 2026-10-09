package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Slim model summary for the embeddable partner tile (ev-monitor-tile.js).
 * hasData=false is the fallback for unknown slugs and models without measured consumption;
 * the tile then asks the reader to contribute. Ranges are 90 % of the net battery divided by
 * the real consumption, rounded to 10 km (same rule as the model page hero), null when the
 * underlying value is missing. Seasonal ranges need at least 10 logs per season.
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
        String modelUrl
) {
    static ModelTileResponse unknown() {
        return new ModelTileResponse(false, null, null, null, null, null, 0, 0, null);
    }
}
