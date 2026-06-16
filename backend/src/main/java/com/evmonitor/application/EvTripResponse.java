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
        String feedback,
        ClimateSummary climate
) {
    /**
     * Climate/comfort loads active during the trip and for how long, parsed from
     * {@code telemetry_extras}. Null for trips without Tesla-FULL climate data
     * (the logfeed then renders no markers). {@code seconds} per load, plus the total
     * {@code tripSeconds} so the UI can show the share of the drive.
     */
    public record ClimateSummary(
            int tripSeconds,
            Load comfortHeat,
            Load hvacHeating,
            Load hvacCooling,
            Load batteryHeater
    ) {
        public boolean anyActive() {
            return comfortHeat.active() || hvacHeating.active()
                    || hvacCooling.active() || batteryHeater.active();
        }
    }

    public record Load(boolean active, int seconds) {}

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
                .climate(TripClimateExtras.parse(trip.getTelemetryExtras()))
                .build();
    }
}
