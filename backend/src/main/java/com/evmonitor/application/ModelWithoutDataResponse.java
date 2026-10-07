package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * A model with a WLTP spec but no community logs yet. Listed below the ranking on the
 * model overview with its spec values only (no driver data, no consumption formula).
 */
public record ModelWithoutDataResponse(
        String brand,
        String model,
        String brandDisplayName,
        String modelDisplayName,
        String modelUrlSlug,
        String category,
        String categoryDisplayName,
        BigDecimal minWltpConsumptionKwhPer100km,
        BigDecimal avgWltpConsumptionKwhPer100km,
        BigDecimal maxWltpConsumptionKwhPer100km,
        BigDecimal minNetCapacityKwh,
        BigDecimal maxNetCapacityKwh
) {}
