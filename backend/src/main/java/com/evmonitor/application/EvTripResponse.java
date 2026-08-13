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
        ClimateSummary climate,
        String locationStartGeohash,
        String locationEndGeohash
) {
    /**
     * Geohash precision handed to the client. 8 characters ≈ 38 m x 19 m - fine enough to
     * place a trip on a map at street level. Rows that store a finer hash are cut down
     * instead of being passed through.
     */
    private static final int MAX_GEOHASH_PRECISION = 8;
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

    /** Location-free projection - the default for every trip in a list response. */
    public static EvTripResponse fromDomain(EvTrip trip) {
        return baseBuilder(trip).build();
    }

    /**
     * Adds the coarse start/end geohashes. Reserved for the single trip the dashboard
     * draws a map for; see {@code TripService#getTripsForCar}.
     */
    public static EvTripResponse fromDomainWithLocation(EvTrip trip) {
        return baseBuilder(trip)
                .locationStartGeohash(coarse(trip.getLocationStartGeohash()))
                .locationEndGeohash(coarse(trip.getLocationEndGeohash()))
                .build();
    }

    private static String coarse(String geohash) {
        if (geohash == null) return null;
        return geohash.length() <= MAX_GEOHASH_PRECISION
                ? geohash
                : geohash.substring(0, MAX_GEOHASH_PRECISION);
    }

    private static EvTripResponseBuilder baseBuilder(EvTrip trip) {
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
                .climate(TripClimateExtras.parse(trip.getTelemetryExtras()));
    }
}
