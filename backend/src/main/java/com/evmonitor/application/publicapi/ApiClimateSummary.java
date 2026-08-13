package com.evmonitor.application.publicapi;

import com.evmonitor.application.EvTripResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Climate/comfort loads that ran during a trip, in the public API's snake_case wire format.
 * Deliberately a separate record from the app's {@code EvTripResponse.ClimateSummary}: the
 * internal DTO may be reshaped freely, this one is a published contract.
 *
 * <p>Only Tesla FULL telemetry produces this today; every other data source yields {@code null}.
 */
@Schema(description = "Climate and comfort loads active during the trip, with their run time")
public record ApiClimateSummary(
        @Schema(description = "Length of the trip in seconds - the denominator for each load's share")
        @JsonProperty("trip_seconds") int tripSeconds,
        @Schema(description = "Seat and steering-wheel heating")
        @JsonProperty("comfort_heat") Load comfortHeat,
        @Schema(description = "Cabin heating")
        @JsonProperty("hvac_heating") Load hvacHeating,
        @Schema(description = "Air conditioning")
        @JsonProperty("hvac_cooling") Load hvacCooling,
        @Schema(description = "Battery conditioning")
        @JsonProperty("battery_heater") Load batteryHeater
) {
    @Schema(description = "A single consumer: whether it ran at all, and for how many seconds")
    public record Load(
            @JsonProperty("active") boolean active,
            @JsonProperty("seconds") int seconds) {}

    /** {@code null} in, {@code null} out - a trip without climate data carries no summary. */
    static ApiClimateSummary fromInternal(EvTripResponse.ClimateSummary summary) {
        if (summary == null) return null;
        return new ApiClimateSummary(
                summary.tripSeconds(),
                load(summary.comfortHeat()),
                load(summary.hvacHeating()),
                load(summary.hvacCooling()),
                load(summary.batteryHeater()));
    }

    private static Load load(EvTripResponse.Load load) {
        return new Load(load.active(), load.seconds());
    }
}
