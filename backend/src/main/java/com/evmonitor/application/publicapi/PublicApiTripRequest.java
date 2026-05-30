package com.evmonitor.application.publicapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicApiTripRequest(
        @NotNull @JsonProperty("car_id")
        UUID carId,

        @Schema(description = "Trip start time. ISO 8601 with timezone offset, e.g. `2025-06-01T08:00:00+02:00`.")
        @NotNull @JsonProperty("started_at")
        String startedAt,

        @Schema(description = "Trip end time. ISO 8601 with timezone offset. Must be after `started_at`.")
        @NotNull @JsonProperty("ended_at")
        String endedAt,

        @Schema(description = "Distance driven in km.")
        @DecimalMin("0.0") @DecimalMax("100000.0") @JsonProperty("distance_km")
        BigDecimal distanceKm,

        @Schema(description = "State of Charge at trip start in percent (0-100).")
        @DecimalMin("0.0") @DecimalMax("100.0") @JsonProperty("soc_start")
        BigDecimal socStart,

        @Schema(description = "State of Charge at trip end in percent (0-100).")
        @DecimalMin("0.0") @DecimalMax("100.0") @JsonProperty("soc_end")
        BigDecimal socEnd,

        @Schema(description = "Route type. Allowed values: `CITY`, `COMBINED`, `HIGHWAY`.",
                allowableValues = {"CITY", "COMBINED", "HIGHWAY"})
        @Size(max = 20) @JsonProperty("route_type")
        String routeType
) {}
