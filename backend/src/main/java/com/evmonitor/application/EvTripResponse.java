package com.evmonitor.application;

import com.evmonitor.domain.EvTrip;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
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
        BigDecimal avgSpeedKmh,
        BigDecimal maxSpeedKmh,
        String routeType,
        String status,
        String dataSource,
        String feedback
) {
    public static EvTripResponse fromDomain(EvTrip trip) {
        return EvTripResponse.builder()
                .id(trip.getId())
                .type("TRIP")
                .carId(trip.getCarId())
                .tripStartedAt(trip.getTripStartedAt())
                .tripEndedAt(trip.getTripEndedAt())
                .distanceKm(trip.getDistanceKm())
                .odometerStartKm(trip.getOdometerStartKm())
                .odometerEndKm(trip.getOdometerEndKm())
                .socStart(trip.getSocStart())
                .socEnd(trip.getSocEnd())
                .energyRemainingStartKwh(trip.getEnergyRemainingStartKwh())
                .energyRemainingEndKwh(trip.getEnergyRemainingEndKwh())
                .outsideTempCelsius(trip.getOutsideTempCelsius())
                .estimatedConsumedKwh(trip.getEstimatedConsumedKwh())
                .avgSpeedKmh(trip.getAvgSpeedKmh())
                .maxSpeedKmh(trip.getMaxSpeedKmh())
                .routeType(trip.getRouteType())
                .status(trip.getStatus())
                .dataSource(trip.getDataSource())
                .feedback(trip.getFeedback())
                .build();
    }
}
