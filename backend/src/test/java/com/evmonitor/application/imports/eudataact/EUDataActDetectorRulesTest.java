package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regeln der Detektoren an synthetischen Minimal-Exports. Diese Faelle lassen sich in den
 * echten Dateien nicht herstellen, entscheiden aber, ob wir Muell importieren oder nicht.
 */
class EUDataActDetectorRulesTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T08:00:00Z");

    private EUDataActJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new EUDataActJsonParser(new ObjectMapper());
    }

    private record Entry(String field, String value, String timestamp) {}

    private InputStream export(List<Entry> entries) {
        String data = entries.stream()
                .map(e -> """
                        {"dataFieldName":"%s","value":"%s","timestampUtc":"%s"}"""
                        .formatted(e.field(), e.value(), e.timestamp()))
                .collect(Collectors.joining(","));
        return new ByteArrayInputStream(
                """
                {"vin":"TESTVIN","Data":[%s]}""".formatted(data).getBytes(StandardCharsets.UTF_8));
    }

    private String at(int minutesFromStart) {
        return T0.plusMinutes(minutesFromStart).toString();
    }

    /**
     * SoC-Zeitreihe mit dem Fingerabdruck des echten Signals: Wertebereich 0-100 und
     * halbzahlige Werte. Faellt langsam (Standverlust), damit die Reihe lang genug ist,
     * ohne einen Ladevorgang vorzutaeuschen.
     */
    private List<Entry> socBaseline(String signal) {
        List<Entry> out = new ArrayList<>();
        double soc = 80.0;
        for (int i = 0; i < 60; i++) {
            out.add(new Entry(signal, String.valueOf(soc), at(-600 + i * 5)));
            soc -= 0.5;
        }
        return out;
    }

    /** Kilometerstand-Fingerabdruck: ganzzahlig, monoton steigend, Spanne > 100. */
    private List<Entry> odometerBaseline(String signal) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            out.add(new Entry(signal, String.valueOf(1000 + i * 3), at(-600 + i * 5)));
        }
        return out;
    }

    /** Auto steht ab jetzt - Kilometerstand konstant. */
    private List<Entry> parked(String signal, int fromMinutes, int toMinutes) {
        return List.of(
                new Entry(signal, "1177", at(fromMinutes)),
                new Entry(signal, "1177", at(toMinutes)));
    }

    private List<Entry> merge(List<Entry>... parts) {
        List<Entry> all = new ArrayList<>();
        for (List<Entry> p : parts) all.addAll(p);
        return all;
    }

    // ── Signal-Identifikation ─────────────────────────────────────────────────

    @Test
    void ambiguousSignals_detectorDeclines() {
        // Zwei Zeitreihen tragen denselben SoC-Fingerabdruck - welche der SoC ist, waere geraten.
        // Lieber ablehnen als die falsche importieren.
        InputStream json = export(merge(
                socBaseline("111111"),
                socBaseline("333333"),
                odometerBaseline("222222")));

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @Test
    void noSocSignal_detectorDeclines() {
        // Nur ganzzahlige Enums und ein Kilometerstand - kein SoC erkennbar.
        List<Entry> enums = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            enums.add(new Entry("999999", String.valueOf(i % 8 + 1), at(-600 + i * 5)));
        }
        InputStream json = export(merge(enums, odometerBaseline("222222")));

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @Test
    void integerEnumsAreNotMistakenForSoc() throws Exception {
        // Thermomanagement-Enums (Werte 1-12) liegen ebenfalls im Bereich 0-100 und sind
        // meist konstant. Sie duerfen den SoC nicht verdraengen - Unterschied: keine halben Werte.
        List<Entry> enums = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            enums.add(new Entry("999999", String.valueOf(i % 12 + 1), at(-600 + i * 5)));
        }
        InputStream json = export(merge(
                socBaseline("111111"),
                enums,
                odometerBaseline("222222"),
                parked("222222", 0, 60),
                List.of(new Entry("111111", "50.5", at(10)),
                        new Entry("111111", "60.5", at(40)))));

        List<EUDataActSession> sessions = parser.parse(json).sessions();

        assertEquals(1, sessions.size());
        assertEquals(10.0, sessions.get(0).socDeltaPct(), 0.01);
    }

    @Test
    void invalidOdometerMarkersAreIgnored() throws Exception {
        // Echte Exports enthalten 1048574 (0xFFFFE) als "Signal ungueltig". Wird der Wert
        // mitgelesen, sieht es aus, als sei das Auto mitten in der Ladung gefahren.
        InputStream json = export(merge(
                socBaseline("111111"),
                odometerBaseline("222222"),
                parked("222222", 0, 60),
                List.of(new Entry("222222", "1048574", at(20)),
                        new Entry("111111", "50.5", at(10)),
                        new Entry("111111", "55.5", at(25)),
                        new Entry("111111", "60.5", at(40)))));

        List<EUDataActSession> sessions = parser.parse(json).sessions();

        assertEquals(1, sessions.size(), "Der Ungueltig-Marker darf die Ladung nicht zerschneiden");
        assertEquals(10.0, sessions.get(0).socDeltaPct(), 0.01);
    }

    // ── Format-Prioritaet ─────────────────────────────────────────────────────

    @Test
    void namedFieldsWinOverSignalIds() throws Exception {
        // Liegen beide Formate vor, gewinnt das mit echter Ladeleistung.
        InputStream json = export(merge(
                socBaseline("111111"),
                odometerBaseline("222222"),
                List.of(
                        new Entry("chargingState", "CHARGING", at(0)),
                        new Entry("chargingState", "CHARGING", at(20)),
                        new Entry("chargingState", "READY_FOR_CHARGING", at(30)),
                        new Entry("chargePowerInKW", "11.0", at(0)),
                        new Entry("chargePowerInKW", "11.0", at(20)),
                        new Entry("chargeType", "AC", at(10)),
                        new Entry("currentSOCInPct", "50", at(0)),
                        new Entry("currentSOCInPct", "60", at(30)))));

        List<EUDataActSession> sessions = parser.parse(json).sessions();

        assertEquals(1, sessions.size());
        // Der SoC-Detektor liefert weder Ladetyp noch Leistung - die hier sind der Beweis,
        // dass der andere Detektor gegriffen hat.
        assertEquals("AC", sessions.get(0).chargeType());
        assertEquals(11.0, sessions.get(0).maxChargingPowerKw());
        assertNotNull(sessions.get(0).calculatedKwh());
    }

    // ── Session-Grenzen im SoC-Pfad ───────────────────────────────────────────

    @Test
    void reportingGapSplitsSessions() throws Exception {
        // Auto steht durchgehend, aber zwischen den Anstiegen liegen 100 Minuten ohne
        // Meldung -> zwei Ladungen, nicht eine durchgehende.
        InputStream json = export(merge(
                socBaseline("111111"),
                odometerBaseline("222222"),
                parked("222222", 0, 200),
                List.of(new Entry("111111", "50.5", at(10)),
                        new Entry("111111", "55.5", at(20)),
                        new Entry("111111", "60.5", at(120)),
                        new Entry("111111", "65.5", at(130)))));

        List<EUDataActSession> sessions = parser.parse(json).sessions();

        assertEquals(2, sessions.size());
        assertEquals(51, sessions.get(0).socBefore());
        assertEquals(56, sessions.get(0).socAfter());
        assertEquals(61, sessions.get(1).socBefore());
        assertEquals(66, sessions.get(1).socAfter());
    }

    @Test
    void tinySocRiseIsNotASession() throws Exception {
        // 1 % Anstieg ist Messrauschen, kein Ladevorgang.
        InputStream json = export(merge(
                socBaseline("111111"),
                odometerBaseline("222222"),
                parked("222222", 0, 60),
                List.of(new Entry("111111", "50.5", at(10)),
                        new Entry("111111", "51.5", at(20)))));

        assertTrue(parser.parse(json).sessions().isEmpty());
    }

    @Test
    void drivingSplitsSessions() throws Exception {
        // Kilometerstand aendert sich zwischen den Anstiegen -> das Auto ist gefahren.
        // Das ist eine Tatsache aus den Daten, keine Heuristik.
        InputStream json = export(merge(
                socBaseline("111111"),
                odometerBaseline("222222"),
                List.of(new Entry("222222", "1177", at(0)),
                        new Entry("222222", "1219", at(25)),
                        new Entry("111111", "50.5", at(10)),
                        new Entry("111111", "55.5", at(20)),
                        new Entry("111111", "60.5", at(30)),
                        new Entry("111111", "65.5", at(40)))));

        List<EUDataActSession> sessions = parser.parse(json).sessions();

        assertEquals(2, sessions.size());
        assertEquals(1177, sessions.get(0).odometerKm());
        assertEquals(1219, sessions.get(1).odometerKm());
    }
}
