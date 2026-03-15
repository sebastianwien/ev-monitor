package com.evmonitor.application.publicapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record PublicApiSessionRequest(
        @NotNull @JsonProperty("car_id") UUID carId,
        @NotNull @NotEmpty @Size(max = 100) @Valid List<SessionEntry> sessions
) {
    public record SessionEntry(
            @NotNull @JsonProperty("date") String date,
            @NotNull @DecimalMin("0.0") @DecimalMax("10000.0") @JsonProperty("kwh") Double kwh,
            @Min(0) @Max(2_000_000) @JsonProperty("odometer_km") Integer odometerKm,
            @Min(0) @Max(100) @JsonProperty("soc_before") Integer socBefore,
            @Min(0) @Max(100) @JsonProperty("soc_after") Integer socAfter,
            @DecimalMin("0.0") @DecimalMax("10000.0") @JsonProperty("cost_eur") Double costEur,
            @Min(0) @Max(100_000) @JsonProperty("duration_min") Integer durationMin,
            @Size(max = 50) @JsonProperty("location") String location,
            @Size(max = 10) @JsonProperty("charging_type") String chargingType,
            @DecimalMin("0.0") @DecimalMax("1000.0") @JsonProperty("max_charging_power_kw") Double maxChargingPowerKw,
            @Size(max = 10) @JsonProperty("route_type") String routeType,
            @Size(max = 10) @JsonProperty("tire_type") String tireType
    ) {}
}
