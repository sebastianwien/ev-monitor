package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PowerCurveResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Achsen des Ladekurven-Vorschaubildes: Gitterlinien in kW, Dauer-Marken auf
 * der Zeitachse und der Ladestand darunter.
 *
 * Getrennt vom Renderer, weil das hier reine Rechnung ist - ohne AWT testbar.
 *
 * <p>Die Staffelung der Gitterlinien und die Ableitung des Ladestands spiegeln
 * bewusst {@code frontend/src/components/charging/powerCurveSeries.ts}: das Bild
 * soll dieselbe Kurve zeigen wie die Ansicht, aus der es geteilt wurde.
 */
final class CurveAxis {

    /** Fuenf Marken auf der Zeitachse - mehr wird bei 1200 px Breite eng. */
    static final int X_TICKS = 5;

    private CurveAxis() {}

    /**
     * Schrittweite der Gitterlinien in kW. Feinste Stufe, die mit hoechstens
     * sechs Linien auskommt - eine feste Schrittweite passt entweder zur
     * 11-kW-AC-Ladung oder zur 250-kW-Schnellladung, nie zu beiden.
     */
    static int yStepKw(double maxKw) {
        int[] steps = {10, 25, 50, 100, 200};
        for (int step : steps) {
            if (maxKw / step <= 6) return step;
        }
        return steps[steps.length - 1];
    }

    /** Beschriftete Gitterlinien von 0 bis ueber die Spitze hinaus. */
    static List<Integer> yTicksKw(double maxKw) {
        int step = yStepKw(maxKw);
        List<Integer> ticks = new ArrayList<>();
        for (int kw = 0; kw <= Math.ceil(maxKw / step) * step; kw += step) ticks.add(kw);
        return ticks;
    }

    /**
     * Beschriftung der Zeitachse als Dauer seit Ladebeginn.
     *
     * Bewusst keine Uhrzeit: wann jemand laedt, ist ein Bewegungsmuster, und
     * {@code PublicCurveResponse} gibt aus demselben Grund nur das Datum preis.
     */
    static List<String> xLabels(List<PowerCurveResponse.Point> points) {
        long spanMs = points.size() < 2 ? 0
                : points.get(points.size() - 1).ts() - points.get(0).ts();

        List<String> labels = new ArrayList<>(X_TICKS);
        for (int i = 0; i < X_TICKS; i++) {
            long ms = spanMs * i / (X_TICKS - 1);
            // Ein einzelner Punkt spannt keine Zeit auf - dann steht ueberall die 0.
            if (i == 0 || spanMs <= 0) labels.add("0");
            else if (spanMs < 120_000L) labels.add(Math.round(ms / 1000.0) + " Sek");
            else labels.add(Math.round(ms / 60_000.0) + " Min");
        }
        return labels;
    }

    /**
     * Ladestand je Kurvenpunkt, aus zwei Quellen in dieser Reihenfolge:
     * gemessen (der Connector haengt ihn an jeden Punkt) oder aus der kumulierten
     * Energie rekonstruiert und auf {@code socBefore -> socAfter} normiert.
     *
     * Der abgeleitete Verlauf setzt konstante Ladeeffizienz voraus; er bildet den
     * Taper korrekt ab, ist aber eine Rekonstruktion und keine Messung.
     *
     * @return null, wenn keine der beiden Quellen traegt - dann bleibt die
     *         SoC-Achse im Bild weg.
     */
    static double[] socSeries(List<PowerCurveResponse.Point> points, BigDecimal socBefore, BigDecimal socAfter) {
        if (points == null || points.isEmpty()) return null;

        if (points.stream().allMatch(p -> p.soc() != null && Double.isFinite(p.soc()))) {
            return points.stream().mapToDouble(PowerCurveResponse.Point::soc).toArray();
        }

        if (points.size() < 2 || socBefore == null || socAfter == null) return null;
        double from = socBefore.doubleValue();
        double span = socAfter.doubleValue() - from;
        if (!(span > 0)) return null;

        double[] cum = cumulativeKwh(points);
        double total = cum[cum.length - 1];
        if (!(total > 0)) return null;

        double[] soc = new double[points.size()];
        for (int i = 0; i < soc.length; i++) soc[i] = from + (cum[i] / total) * span;
        return soc;
    }

    /**
     * Ladestand an einem Bruchteil der Zeitspanne, linear zwischen den Punkten
     * interpoliert - die Achsenmarken sitzen fast nie auf einem Messpunkt.
     */
    static double socAtRatio(List<PowerCurveResponse.Point> points, double[] soc, double ratio) {
        int n = Math.min(points.size(), soc.length);
        if (n == 0) return 0;
        if (n == 1) return soc[0];

        long first = points.get(0).ts();
        long span = points.get(n - 1).ts() - first;
        if (span <= 0) return soc[n - 1];

        long ts = first + Math.round(span * ratio);
        if (ts <= first) return soc[0];
        if (ts >= points.get(n - 1).ts()) return soc[n - 1];

        for (int i = 1; i < n; i++) {
            if (points.get(i).ts() < ts) continue;
            long dt = points.get(i).ts() - points.get(i - 1).ts();
            if (dt <= 0) return soc[i];
            double r = (double) (ts - points.get(i - 1).ts()) / dt;
            return soc[i - 1] + r * (soc[i] - soc[i - 1]);
        }
        return soc[n - 1];
    }

    /**
     * Zeitgewichteter Mittelwert der Leistung - dieselbe Rechnung wie die Kachel
     * "Schnitt" in der App: Kurvenintegral geteilt durch die Kurvenspanne.
     *
     * @return null, wenn die Kurve keine Zeitspanne aufspannt.
     */
    static Double avgKw(List<PowerCurveResponse.Point> points) {
        if (points == null || points.size() < 2) return null;
        double hours = (points.get(points.size() - 1).ts() - points.get(0).ts()) / 3_600_000.0;
        if (!(hours > 0)) return null;

        double[] cum = cumulativeKwh(points);
        return cum[cum.length - 1] / hours;
    }

    /**
     * Kumulierte Energie in kWh je Punkt, Trapezregel ueber (kW, ts).
     *
     * Die Punkte kommen ungleichmaessig - Tesla streamt on-change. Index-basiertes
     * Aufsummieren wuerde dichte Messphasen ueberbewerten.
     */
    private static double[] cumulativeKwh(List<PowerCurveResponse.Point> points) {
        double[] cum = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            double dtH = (points.get(i).ts() - points.get(i - 1).ts()) / 3_600_000.0;
            cum[i] = cum[i - 1] + ((points.get(i).kw() + points.get(i - 1).kw()) / 2) * dtH;
        }
        return cum;
    }
}
