package com.evmonitor.application;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record InternalTripRequest(
        UUID externalId,
        UUID carId,
        UUID userId,
        String dataSource,
        OffsetDateTime tripStartedAt,
        OffsetDateTime tripEndedAt,
        BigDecimal socStart,
        BigDecimal socEnd,
        BigDecimal odometerStartKm,
        BigDecimal odometerEndKm,
        BigDecimal distanceKm,
        String locationStartGeohash,
        String locationEndGeohash,
        BigDecimal outsideTempCelsius,
        BigDecimal energyRemainingStartKwh,
        BigDecimal energyRemainingEndKwh,
        BigDecimal estimatedConsumedKwh,
        BigDecimal avgSpeedKmh,
        BigDecimal maxSpeedKmh,
        String status,
        String rawPayload,
        String telemetryExtras,
        /** Gefahrene Linie aus den Location-Beacons; null, wenn die Quelle keine Position streamt. */
        String tracePolyline
) {}
