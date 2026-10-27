package com.evmonitor.application;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for the live-charging history endpoint.
 *
 * Returns a chronological list of {@link Point}s of the running charge. An empty list is the safe fallback
 * when the connectors-service is unavailable.
 */
public record LiveChargingHistoryResponse(List<Point> points) {

    /** {@code powerKw} fehlt bei Quellen ohne Leistungsmessung (Smartcar), {@code socPercent} bei Tesla. */
    public record Point(Instant timestamp, Double powerKw, Double socPercent) {}

    /** Empty placeholder - returned when connectors is unreachable. */
    public static LiveChargingHistoryResponse empty() {
        return new LiveChargingHistoryResponse(List.of());
    }
}
