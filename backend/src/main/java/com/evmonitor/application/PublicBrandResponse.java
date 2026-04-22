package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public response for a brand page — lists all models with WLTP and community data summary.
 */
public record PublicBrandResponse(
        String brandEnum,
        String brandDisplayName,
        List<ModelSummary> models
) {
    public record ModelSummary(
            String modelEnum,
            String modelDisplayName,
            String modelUrlSlug,
            int logCount,
            BigDecimal avgConsumptionKwhPer100km,  // null if no community data
            List<WltpVariantSummary> wltpVariants  // all known battery variants
    ) {}

    public record WltpVariantSummary(
            BigDecimal batteryCapacityKwh,
            String variantName,
            String displayLabel,
            BigDecimal realConsumptionKwhPer100km,
            BigDecimal realConsumptionMinKwhPer100km,
            BigDecimal realConsumptionMaxKwhPer100km
    ) {}
}
