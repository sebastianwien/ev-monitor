package com.evmonitor.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

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
        BigDecimal nominalFullPackKwh,
        BigDecimal estimatedConsumedKwh,
        String status,
        String rawPayload
) {}
