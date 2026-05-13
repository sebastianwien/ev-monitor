package com.evmonitor.application;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for the live-charging history endpoint.
 *
 * Returns a chronological list of {@link Point}s where each point is a merged
 * charging-power sample at a given timestamp. An empty list is the safe fallback
 * when the connectors-service is unavailable.
 */
public record LiveChargingHistoryResponse(List<Point> points) {

    public record Point(Instant timestamp, double powerKw) {}

    /** Empty placeholder - returned when connectors is unreachable. */
    public static LiveChargingHistoryResponse empty() {
        return new LiveChargingHistoryResponse(List.of());
    }
}
