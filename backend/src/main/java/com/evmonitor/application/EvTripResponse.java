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
        BigDecimal odometerStartKm,
        BigDecimal odometerEndKm,
        BigDecimal socStart,
        BigDecimal socEnd,
        BigDecimal energyRemainingStartKwh,
        BigDecimal energyRemainingEndKwh,
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
                trip.getOdometerStartKm(),
                trip.getOdometerEndKm(),
                trip.getSocStart(),
                trip.getSocEnd(),
                trip.getEnergyRemainingStartKwh(),
                trip.getEnergyRemainingEndKwh(),
                trip.getOutsideTempCelsius(),
                trip.getEstimatedConsumedKwh(),
                trip.getRouteType(),
                trip.getStatus(),
                trip.getDataSource(),
                trip.getFeedback()
        );
    }
}
