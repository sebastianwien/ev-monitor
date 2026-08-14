package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Downsampled charging power curve snapshot for a single ev_log entry.
 * Stored as JSONB in {@code ev_log.power_curve_points}, parsed on read.
 * Empty list when the log has no curve (most data sources).
 */
public record PowerCurveResponse(List<Point> points) {

    /**
     * @param soc charge level in percent at {@code ts}. Null for curves written before the
     *            connectors started sampling {@code Soc}, and for sessions where no Soc
     *            events survived - the client derives the progression from the curve then.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Point(long ts, double kw, Double soc) {}

    public static PowerCurveResponse empty() {
        return new PowerCurveResponse(List.of());
    }
}
