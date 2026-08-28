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
        String routePolyline,
        /** "SKETCH" (zwischen den Enden geraten) oder "MATCHED" (aus der Spur gerechnet). */
        String routeKind,
        String tracePolyline,
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

    /**
     * How much of a trip a client is allowed to see. Ort und Telemetrie stehen zusammen: beide
     * beschreiben denselben Vorgang, und wer den Weg sehen darf, darf auch wissen, wie schnell
     * er zurueckgelegt wurde.
     */
    public enum Detail {
        /** Base fields only - an older trip of a user without the analytics entitlement. */
        BASE,
        /**
         * Adds speeds, the climate summary, the start/end geohashes and the line between them.
         * Freigeschaltet fuer die neueste Fahrt (fuer jeden) und fuer alle Fahrten eines
         * Nutzers mit Analytics-Entitlement.
         */
        TELEMETRY_AND_LOCATION
    }

    /**
     * Builds the response at the requested level of detail. Anything above {@link Detail#BASE}
     * is an entitlement decision and belongs to the caller; see {@code TripService}.
     */
    public static EvTripResponse fromDomain(EvTrip trip, Detail detail) {
        EvTripResponseBuilder builder = baseBuilder(trip);
        if (detail != Detail.BASE) {
            builder.avgSpeedKmh(trip.getAvgSpeedKmh())
                    .maxSpeedKmh(trip.getMaxSpeedKmh())
                    .climate(TripClimateExtras.parse(trip.getTelemetryExtras()));
        }
        if (detail == Detail.TELEMETRY_AND_LOCATION) {
            builder.locationStartGeohash(coarse(trip.getLocationStartGeohash()))
                    .locationEndGeohash(coarse(trip.getLocationEndGeohash()))
                    // Die Linie gehoert zum selben Ortsbezug: wer die Gegend sehen darf,
                    // darf auch den Weg dazwischen sehen - und nur der. Beide Formen gehen
                    // getrennt raus, damit der Client die gefahrene Spur von der gerechneten
                    // Strasse unterscheiden und sie richtig beschriften kann.
                    .routePolyline(trip.getRoutePolyline())
                    .routeKind(trip.getRouteKind())
                    .tracePolyline(trip.getTracePolyline());
        }
        return builder.build();
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
                .routeType(trip.getRouteType())
                .status(trip.getStatus())
                .dataSource(trip.getDataSource())
                .feedback(trip.getFeedback());
    }
}
