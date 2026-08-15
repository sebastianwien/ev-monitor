package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Der aufgezeichnete Verlauf einer Ladung - in einer von zwei Auspraegungen,
 * je nachdem was die Datenquelle hergibt.
 *
 * <p>{@code points} ist die Ladekurve: gemessene Leistung ueber die Zeit, aus
 * {@code ev_log.power_curve_points}. Nur Quellen mit Live-Leistung liefern sie
 * (aktuell Tesla-Telemetrie).
 *
 * <p>{@code socPoints} ist der Ladeverlauf: gemessener Ladestand ueber die Zeit,
 * aus {@code ev_log.soc_curve_points}. Fuer Quellen ohne Leistungsmessung
 * (Smartcar). Bewusst keine daraus abgeleitete kW-Reihe - die waere ein
 * Vier-Minuten-Mittel und keine Momentanleistung.
 *
 * <p>Beide Listen sind leer, wenn zu der Ladung nichts aufgezeichnet wurde -
 * der Normalfall fuer die meisten Datenquellen.
 */
public record PowerCurveResponse(List<Point> points, List<SocPoint> socPoints) {

    /**
     * @param soc charge level in percent at {@code ts}. Null for curves written before the
     *            connectors started sampling {@code Soc}, and for sessions where no Soc
     *            events survived - the client derives the progression from the curve then.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Point(long ts, double kw, Double soc) {}

    /** Ein gemessener Ladestand. Keine Leistung - siehe Klassenkommentar. */
    public record SocPoint(long ts, double soc) {}

    public static PowerCurveResponse empty() {
        return new PowerCurveResponse(List.of(), List.of());
    }

    /** Nur die Leistungskurve - fuer Quellen, die keinen Ladeverlauf mitbringen. */
    public static PowerCurveResponse ofPower(List<Point> points) {
        return new PowerCurveResponse(points, List.of());
    }
}
