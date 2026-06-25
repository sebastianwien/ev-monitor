package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Lightweight model summary for top-N lists (landing page, model index).
 * Contains just enough data to render a model card with real vs. WLTP comparison.
 * realRangeKm: best per-variant real range (net capacity / real consumption × 100),
 * null unless a variant has enough trips — used by the range ranking.
 */
public record TopModelResponse(
        String brand,
        String model,
        String brandDisplayName,
        String modelDisplayName,
        String modelUrlSlug,
        int logCount,
        BigDecimal avgConsumptionKwhPer100km,
        BigDecimal minRealConsumptionKwhPer100km,
        BigDecimal maxRealConsumptionKwhPer100km,
        BigDecimal minWltpConsumptionKwhPer100km,
        BigDecimal maxWltpConsumptionKwhPer100km,
        BigDecimal avgCostPerKwh,
        String category,
        String categoryDisplayName,
        BigDecimal realRangeKm
) {}
