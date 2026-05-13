package com.evmonitor.application;

import java.util.List;

/**
 * Downsampled charging power curve snapshot for a single ev_log entry.
 * Stored as JSONB in {@code ev_log.power_curve_points}, parsed on read.
 * Empty list when the log has no curve (most data sources).
 */
public record PowerCurveResponse(List<Point> points) {

    public record Point(long ts, double kw) {}

    public static PowerCurveResponse empty() {
        return new PowerCurveResponse(List.of());
    }
}
