package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @param modelComparisons je Variante (Spec) des Modells
 * @param allVariants      alle Autos aller Varianten des Modells zusammen, ohne die Autos des Nutzers;
 *                         null, wenn das Auto keine Spec hat
 */
public record PeerModelComparisonResponse(
        List<PeerModelComparisonItem> modelComparisons,
        PeerMetrics allVariants
) {}

record PeerModelComparisonItem(
        UUID vehicleSpecificationId,
        String displayName,
        boolean isUserVehicleSpec,
        BigDecimal netBatteryCapacityKwh,
        UserMetrics userMetrics,  // null wenn nicht die user spec
        PeerMetrics peerMetrics
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
