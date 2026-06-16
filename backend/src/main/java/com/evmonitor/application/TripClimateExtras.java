package com.evmonitor.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses the climate/comfort block out of {@code ev_trip.telemetry_extras} into the
 * {@link EvTripResponse.ClimateSummary} the logfeed renders as markers. The slot is shared
 * across telemetry sources (each with its own schema), so only blobs carrying a {@code climate}
 * object with at least one active load yield a summary - everything else returns {@code null}
 * and no marker is shown.
 */
final class TripClimateExtras {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TripClimateExtras() {}

    static EvTripResponse.ClimateSummary parse(String telemetryExtrasJson) {
        if (telemetryExtrasJson == null || telemetryExtrasJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(telemetryExtrasJson);
            JsonNode climate = root.path("climate");
            if (!climate.isObject()) {
                return null;
            }
            EvTripResponse.ClimateSummary summary = new EvTripResponse.ClimateSummary(
                    root.path("tripSeconds").asInt(0),
                    load(climate.path("comfortHeat")),
                    load(climate.path("hvacHeating")),
                    load(climate.path("hvacCooling")),
                    load(climate.path("batteryHeater")));
            return summary.anyActive() ? summary : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static EvTripResponse.Load load(JsonNode node) {
        if (!node.isObject()) {
            return new EvTripResponse.Load(false, 0);
        }
        return new EvTripResponse.Load(
                node.path("active").asBoolean(false),
                node.path("seconds").asInt(0));
    }
}
