package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Lightweight model summary for top-N lists (landing page, model index).
 * Contains just enough data to render a model card with real vs. WLTP comparison.
 */
public record TopModelResponse(
        String brand,
        String model,
        String brandDisplayName,
        String modelDisplayName,
        String modelUrlSlug,
        int logCount,
        BigDecimal avgConsumptionKwhPer100km,
        BigDecimal bestWltpConsumptionKwhPer100km,
        BigDecimal avgCostPerKwh
) {}
