package com.evmonitor.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PeerModelComparisonResponse(
        List<PeerModelComparisonItem> modelComparisons
) {}

record PeerModelComparisonItem(
        UUID vehicleSpecificationId,
        String displayName,
        boolean isUserVehicleSpec,
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
