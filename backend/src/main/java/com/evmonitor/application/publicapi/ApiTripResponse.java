package com.evmonitor.application.publicapi;

import com.evmonitor.domain.EvTrip;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "A driving trip")
public record ApiTripResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("car_id") UUID carId,
        @JsonProperty("data_source") String dataSource,
        @JsonProperty("started_at") String startedAt,
        @JsonProperty("ended_at") String endedAt,
        @JsonProperty("distance_km") BigDecimal distanceKm,
        @JsonProperty("odometer_start_km") BigDecimal odometerStartKm,
        @JsonProperty("odometer_end_km") BigDecimal odometerEndKm,
        @JsonProperty("soc_start") BigDecimal socStart,
        @JsonProperty("soc_end") BigDecimal socEnd,
        @JsonProperty("estimated_consumed_kwh") BigDecimal estimatedConsumedKwh,
        @JsonProperty("avg_speed_kmh") BigDecimal avgSpeedKmh,
        @JsonProperty("max_speed_kmh") BigDecimal maxSpeedKmh,
        @JsonProperty("outside_temp_celsius") BigDecimal outsideTempCelsius,
        @JsonProperty("route_type") String routeType,
        @JsonProperty("status") String status
) {
    public static ApiTripResponse fromDomain(EvTrip trip) {
        return new ApiTripResponse(
                trip.getId(),
                trip.getCarId(),
                trip.getDataSource(),
                trip.getTripStartedAt() != null ? trip.getTripStartedAt().toString() : null,
                trip.getTripEndedAt() != null ? trip.getTripEndedAt().toString() : null,
                trip.getDistanceKm(),
                trip.getOdometerStartKm(),
                trip.getOdometerEndKm(),
                trip.getSocStart(),
                trip.getSocEnd(),
                trip.getEstimatedConsumedKwh(),
                trip.getAvgSpeedKmh(),
                trip.getMaxSpeedKmh(),
                trip.getOutsideTempCelsius(),
                trip.getRouteType(),
                trip.getStatus()
        );
    }
}
