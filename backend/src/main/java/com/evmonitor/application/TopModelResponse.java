package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * Lightweight model summary for top-N lists (landing page, model index).
 * Contains just enough data to render a model card with real vs. WLTP comparison.
 * realRangeKm: best per-variant real range (net capacity × RangeWindow.SHARE / real consumption × 100),
 * null unless a variant has enough trips - used by the range ranking.
 * avgWltpConsumptionKwhPer100km: mean over the model's WLTP spec rows (same source as min/max).
 * summer/winterConsumptionKwhPer100km: community seasonal consumption from EvLogStatisticsService,
 * null when a season has no plausible trips.
 * contributorCount/carCount: drivers and cars behind logCount (data basis shown per row).
 * min/maxNetCapacityKwh: smallest and largest net battery of the model's WLTP specs.
 * typicalRangeMin/MaxKm: net capacity (small/large battery) on the range window (RangeMath) with the
 * model's range consumption (RangeConsumptionPolicy: other drivers' trips, else the estimate);
 * winterRangeMin/MaxKm: same with the winter basis, null without winter data.
 * The trailing fields are nullable so an older frontend (Blue/Green) can ignore them.
 */
public record TopModelResponse(
        String brand,
        String model,
        String brandDisplayName,
        /** Marke im URL-Slug der Modellseiten ("Volkswagen"), bleibt stabil; brandDisplayName ist das Label ("VW"). */
        String brandSlug,
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
        BigDecimal realRangeKm,
        BigDecimal avgWltpConsumptionKwhPer100km,
        BigDecimal summerConsumptionKwhPer100km,
        BigDecimal winterConsumptionKwhPer100km,
        Integer contributorCount,
        Integer carCount,
        BigDecimal minNetCapacityKwh,
        BigDecimal maxNetCapacityKwh,
        Integer typicalRangeMinKm,
        Integer typicalRangeMaxKm,
        Integer winterRangeMinKm,
        Integer winterRangeMaxKm,
        /** Fast-charge power on a short stop from a low SoC (75th percentile), null below 8 sessions */
        BigDecimal fastChargePowerKw,
        /**
         * The consumption the list shows and the range rests on (RangeConsumptionPolicy, public: other
         * drivers' trips, else their charges, else WLTP × fleet factor) with its source name and, for
         * PEER_DRIVE, the drivers behind it. Only measured sources (PEER_DRIVE, PEER_CHARGES) may rank.
         */
        BigDecimal rangeConsumptionKwhPer100km,
        String rangeConsumptionSource,
        Integer rangeConsumptionDrivers
) {}
