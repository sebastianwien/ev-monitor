package com.evmonitor.infrastructure.image;

import com.evmonitor.application.PowerCurveResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die reinen Achsen-Berechnungen des Vorschaubildes. Bewusst ohne AWT: was hier
 * geprueft wird, sind Zahlen - die Beschriftung der Gitterlinien und der aus der
 * Kurve abgeleitete Ladestand.
 */
class CurveAxisTest {

    // --- Y-Achse: Schrittweite -------------------------------------------------

    @Test
    void yStepKeepsGridBelowSevenLines() {
        // Dieselbe Staffelung wie im Frontend (yTickStepKw): feinste Stufe, die
        // mit hoechstens sechs Gitterlinien auskommt.
        assertEquals(10, CurveAxis.yStepKw(11));    // AC-Ladung
        assertEquals(25, CurveAxis.yStepKw(120));
        assertEquals(50, CurveAxis.yStepKw(250));   // typische Schnellladung
        assertEquals(100, CurveAxis.yStepKw(400));
    }

    @Test
    void yStepNeverZeroForTinyCurves() {
        // Eine Schrittweite von 0 wuerde die Tick-Schleife nicht terminieren.
        assertTrue(CurveAxis.yStepKw(0) > 0);
        assertTrue(CurveAxis.yStepKw(1.5) > 0);
    }

    @Test
    void yTicksCoverTheCurveAndStartAtZero() {
        List<Integer> ticks = CurveAxis.yTicksKw(250);
        assertEquals(0, ticks.get(0));
        assertTrue(ticks.get(ticks.size() - 1) >= 250, "Oberster Tick muss die Spitze einschliessen");
        assertTrue(ticks.size() <= 7, "Mehr als sieben Beschriftungen ueberladen das Bild");
    }

    // --- X-Achse: Dauer statt Uhrzeit ------------------------------------------

    @Test
    void xLabelsAreDurationsNotClockTimes() {
        // Uhrzeiten waeren ein Bewegungsmuster - PublicCurveResponse gibt bewusst
        // nur das Datum preis, das Bild darf die Uhrzeit nicht hintenrum liefern.
        List<String> labels = CurveAxis.xLabels(points(30, 30_000L));
        assertEquals(5, labels.size());
        assertEquals("0", labels.get(0));
        assertTrue(labels.get(4).endsWith("Min"), "Erwartet Dauer-Label, war: " + labels.get(4));
        labels.forEach(l -> assertFalse(l.contains(":"), "Keine Uhrzeit im Label: " + l));
    }

    @Test
    void xLabelsFallBackToSecondsForVeryShortCurves() {
        // Eine 90-Sekunden-Ladung ergaebe sonst fuenfmal "0 Min".
        List<String> labels = CurveAxis.xLabels(points(4, 30_000L));
        assertEquals(5, labels.size());
        assertTrue(labels.get(4).endsWith("Sek"), "Erwartet Sekunden-Label, war: " + labels.get(4));
    }

    @Test
    void xLabelsHandleSinglePoint() {
        assertEquals(List.of("0", "0", "0", "0", "0"), CurveAxis.xLabels(points(1, 30_000L)));
    }

    // --- Mittlere Leistung -----------------------------------------------------

    @Test
    void avgKwIsTimeWeightedNotIndexWeighted() {
        // Zwei Minuten volle Leistung, danach zwanzig Minuten fast nichts:
        // ein Mittel ueber die Indizes laege deutlich zu hoch.
        List<PowerCurveResponse.Point> points = List.of(
                new PowerCurveResponse.Point(0L, 100.0, null),
                new PowerCurveResponse.Point(60_000L, 100.0, null),
                new PowerCurveResponse.Point(1_260_000L, 0.0, null));

        // 100 kWh/h * 1 Min + Rampe 100->0 ueber 20 Min = 1,667 + 16,667 kWh in 21 Min
        assertEquals(52.4, CurveAxis.avgKw(points), 0.1);
    }

    @Test
    void avgKwIsNullWithoutTimespan() {
        assertNull(CurveAxis.avgKw(points(1, 30_000L)));
        assertNull(CurveAxis.avgKw(List.of()));
    }

    // --- SoC-Achse -------------------------------------------------------------

    @Test
    void socSeriesPrefersMeasuredValues() {
        List<PowerCurveResponse.Point> points = List.of(
                new PowerCurveResponse.Point(0L, 100.0, 20.0),
                new PowerCurveResponse.Point(60_000L, 90.0, 25.0),
                new PowerCurveResponse.Point(120_000L, 80.0, 29.0));

        double[] soc = CurveAxis.socSeries(points, new BigDecimal("10"), new BigDecimal("80"));

        assertNotNull(soc);
        assertArrayEquals(new double[]{20.0, 25.0, 29.0}, soc, 0.001);
    }

    @Test
    void socSeriesDerivesFromEnergyWhenUnmeasured() {
        // Konstante Leistung -> linearer Anstieg zwischen den Log-Grenzen.
        List<PowerCurveResponse.Point> points = List.of(
                new PowerCurveResponse.Point(0L, 100.0, null),
                new PowerCurveResponse.Point(60_000L, 100.0, null),
                new PowerCurveResponse.Point(120_000L, 100.0, null));

        double[] soc = CurveAxis.socSeries(points, new BigDecimal("10"), new BigDecimal("50"));

        assertNotNull(soc);
        assertArrayEquals(new double[]{10.0, 30.0, 50.0}, soc, 0.001);
    }

    @Test
    void socSeriesFollowsTaperNotTime() {
        // Erst voll, dann halbe Leistung: nach der Haelfte der Zeit sind zwei
        // Drittel des Ladehubs erreicht, nicht die Haelfte.
        List<PowerCurveResponse.Point> points = List.of(
                new PowerCurveResponse.Point(0L, 100.0, null),
                new PowerCurveResponse.Point(60_000L, 100.0, null),
                new PowerCurveResponse.Point(120_000L, 0.0, null));

        double[] soc = CurveAxis.socSeries(points, BigDecimal.ZERO, new BigDecimal("30"));

        assertNotNull(soc);
        assertEquals(20.0, soc[1], 0.001);
        assertEquals(30.0, soc[2], 0.001);
    }

    @Test
    void socSeriesIsNullWithoutAnySource() {
        assertNull(CurveAxis.socSeries(points(10, 30_000L), null, null));
        assertNull(CurveAxis.socSeries(points(10, 30_000L), new BigDecimal("50"), new BigDecimal("50")),
                "Ohne Ladehub gibt es nichts zu zeigen");
    }

    @Test
    void socAtRatioInterpolatesBetweenPoints() {
        // Die Achsenbeschriftungen sitzen auf Bruchteilen der Zeitspanne und
        // damit fast nie auf einem Messpunkt.
        List<PowerCurveResponse.Point> points = List.of(
                new PowerCurveResponse.Point(0L, 100.0, 20.0),
                new PowerCurveResponse.Point(100_000L, 100.0, 40.0));
        double[] soc = {20.0, 40.0};

        assertEquals(20.0, CurveAxis.socAtRatio(points, soc, 0.0), 0.001);
        assertEquals(30.0, CurveAxis.socAtRatio(points, soc, 0.5), 0.001);
        assertEquals(40.0, CurveAxis.socAtRatio(points, soc, 1.0), 0.001);
    }

    private static List<PowerCurveResponse.Point> points(int count, long stepMs) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new PowerCurveResponse.Point(1_700_000_000_000L + i * stepMs, 250.0 - i * 5.0, null))
                .toList();
    }
}
