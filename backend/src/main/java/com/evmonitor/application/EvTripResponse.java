package com.evmonitor.application;

import com.evmonitor.domain.EvTrip;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EvTripResponse(
        UUID id,
        String type,
        UUID carId,
        OffsetDateTime tripStartedAt,
        OffsetDateTime tripEndedAt,
        BigDecimal distanceKm,
        BigDecimal socStart,
        BigDecimal socEnd,
        BigDecimal outsideTempCelsius,
        BigDecimal estimatedConsumedKwh,
        String routeType,
        String status,
        String dataSource,
        String feedback
) {
    public static EvTripResponse fromDomain(EvTrip trip) {
        return new EvTripResponse(
                trip.getId(),
                "TRIP",
                trip.getCarId(),
                trip.getTripStartedAt(),
                trip.getTripEndedAt(),
                trip.getDistanceKm(),
                trip.getSocStart(),
                trip.getSocEnd(),
                trip.getOutsideTempCelsius(),
                trip.getEstimatedConsumedKwh(),
                trip.getRouteType(),
                trip.getStatus(),
                trip.getDataSource(),
                trip.getFeedback()
        );
    }
}
