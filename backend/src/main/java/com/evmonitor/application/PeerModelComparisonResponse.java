package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @param modelComparisons je Variante (Spec) des Modells
 * @param allVariants      alle Autos aller Varianten des Modells zusammen, ohne die Autos des Nutzers;
 *                         null, wenn das Auto keine Spec hat
 * @param allVariantsWinterConsumptionKwhPer100km Winterverbrauch (November bis Februar, alle Jahre) derselben
 *                         Autos; null unter 10 Winterladungen oder ohne Spec
 * @param allVariantsDriveWinterConsumptionKwhPer100km Fahrverbrauch im Winter (November bis Februar, alle Jahre)
 *                         anderer Fahrer des Modells aus ihren Fahrten ({@link DriveConsumptionService}); null unter
 *                         2 anderen Nutzern oder ohne Spec. Nur noch für ein älteres Frontend (Blue/Green), die
 *                         Story liest den Winter aus {@code range.winter} der Statistik.
 */
public record PeerModelComparisonResponse(
        List<PeerModelComparisonItem> modelComparisons,
        PeerMetrics allVariants,
        BigDecimal allVariantsWinterConsumptionKwhPer100km,
        BigDecimal allVariantsDriveWinterConsumptionKwhPer100km
) {}

record PeerModelComparisonItem(
        UUID vehicleSpecificationId,
        String displayName,
        boolean isUserVehicleSpec,
        BigDecimal netBatteryCapacityKwh,
        UserMetrics userMetrics,  // null wenn nicht die user spec
        PeerMetrics peerMetrics,
        Integer userRangeKm,      // RangeMath mit der SoH-Kapazität des Nutzerautos; null ohne userMetrics
        Integer peerRangeKm       // RangeMath mit der Netto-Kapazität der Variante
) {}

record UserMetrics(
        BigDecimal consumptionKwhPer100km,
        BigDecimal costPerKwh
) {}

record PeerMetrics(
        BigDecimal avgConsumptionKwhPer100km,
        BigDecimal avgCostPerKwh,
        int uniqueCars
) {}
