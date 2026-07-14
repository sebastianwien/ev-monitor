package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regeln der Detektoren an synthetischen Minimal-Exports. Diese Faelle lassen sich in den
 * echten Dateien nicht herstellen, entscheiden aber, ob wir Muell importieren oder nicht.
 */
class EUDataActDetectorRulesTest {

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
        String json = """
                {"vin":"TESTVIN","Data":[%s]}""".formatted(data);
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private Entry socAnchor() {
        return new Entry("hvsoc_info.value", "50", "2026-01-01T10:00:00Z");
    }

    private Entry odometerAnchor() {
        return new Entry("mileage_info.value", "1000", "2026-01-01T10:00:00Z");
    }

    /** Konstanter Kilometerstand -> das Auto steht, der Detektor trennt nicht. */
    private List<Entry> parkedOdometer() {
        return List.of(
                new Entry("222222", "1000", "2026-01-01T09:00:00Z"),
                new Entry("222222", "1000", "2026-01-01T14:00:00Z"));
    }

    // ── Anker-Aufloesung ──────────────────────────────────────────────────────

    @Test
    void ambiguousAnchor_detectorDeclines() {
        // Zwei Signale tragen zum Ankerzeitpunkt denselben Wert - welches der SoC ist, waere geraten.
        // Lieber ablehnen als die falsche Zeitreihe importieren.
        InputStream json = export(List.of(
                socAnchor(), odometerAnchor(),
                new Entry("111111", "50", "2026-01-01T10:00:00Z"),
                new Entry("111111", "60", "2026-01-01T10:30:00Z"),
                new Entry("333333", "50", "2026-01-01T10:00:00Z"),
                new Entry("333333", "70", "2026-01-01T10:30:00Z"),
                parkedOdometer().get(0), parkedOdometer().get(1)));

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @Test
    void missingAnchor_detectorDeclines() {
        // Ohne Ankerwert ist die Signal-ID nicht zuzuordnen.
        InputStream json = export(List.of(
                new Entry("111111", "50", "2026-01-01T10:00:00Z"),
                new Entry("111111", "60", "2026-01-01T10:30:00Z")));

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    // ── Format-Prioritaet ─────────────────────────────────────────────────────

    @Test
    void namedFieldsWinOverSignalIds() throws Exception {
        // Liegen beide Formate vor, gewinnt das mit echter Ladeleistung.
        InputStream json = export(List.of(
                socAnchor(), odometerAnchor(),
                new Entry("111111", "50", "2026-01-01T10:00:00Z"),
                new Entry("111111", "60", "2026-01-01T10:30:00Z"),
                parkedOdometer().get(0), parkedOdometer().get(1),
                new Entry("chargingState", "CHARGING", "2026-01-01T10:00:00Z"),
                new Entry("chargingState", "CHARGING", "2026-01-01T10:20:00Z"),
                new Entry("chargingState", "READY_FOR_CHARGING", "2026-01-01T10:30:00Z"),
                new Entry("chargePowerInKW", "11.0", "2026-01-01T10:00:00Z"),
                new Entry("chargePowerInKW", "11.0", "2026-01-01T10:20:00Z"),
                new Entry("chargeType", "AC", "2026-01-01T10:10:00Z"),
                new Entry("currentSOCInPct", "50", "2026-01-01T10:00:00Z"),
                new Entry("currentSOCInPct", "60", "2026-01-01T10:30:00Z")));

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
        // Auto steht durchgehend (Kilometerstand konstant), aber zwischen den Anstiegen liegen
        // 100 Minuten ohne Meldung -> zwei Ladungen, nicht eine durchgehende.
        InputStream json = export(List.of(
                socAnchor(), odometerAnchor(),
                new Entry("111111", "50", "2026-01-01T10:00:00Z"),
                new Entry("111111", "55", "2026-01-01T10:10:00Z"),
                new Entry("111111", "60", "2026-01-01T11:50:00Z"),
                new Entry("111111", "65", "2026-01-01T12:00:00Z"),
                parkedOdometer().get(0), parkedOdometer().get(1)));

        List<EUDataActSession> sessions = parser.parse(json).sessions();

        assertEquals(2, sessions.size());
        assertEquals(50, sessions.get(0).socBefore());
        assertEquals(55, sessions.get(0).socAfter());
        assertEquals(60, sessions.get(1).socBefore());
        assertEquals(65, sessions.get(1).socAfter());
    }

    @Test
    void tinySocRiseIsNotASession() throws Exception {
        // 1 % Anstieg ist Messrauschen, kein Ladevorgang.
        InputStream json = export(List.of(
                socAnchor(), odometerAnchor(),
                new Entry("111111", "50", "2026-01-01T10:00:00Z"),
                new Entry("111111", "51", "2026-01-01T10:10:00Z"),
                parkedOdometer().get(0), parkedOdometer().get(1)));

        assertTrue(parser.parse(json).sessions().isEmpty());
    }

    @Test
    void drivingSplitsSessions() throws Exception {
        // Kilometerstand aendert sich zwischen den Anstiegen -> das Auto ist gefahren.
        // Das ist eine Tatsache aus den Daten, keine Heuristik.
        InputStream json = export(List.of(
                socAnchor(), odometerAnchor(),
                new Entry("111111", "50", "2026-01-01T10:00:00Z"),
                new Entry("111111", "55", "2026-01-01T10:10:00Z"),
                new Entry("111111", "60", "2026-01-01T10:20:00Z"),
                new Entry("111111", "65", "2026-01-01T10:30:00Z"),
                new Entry("222222", "1000", "2026-01-01T09:00:00Z"),
                new Entry("222222", "1042", "2026-01-01T10:15:00Z")));

        List<EUDataActSession> sessions = parser.parse(json).sessions();

        assertEquals(2, sessions.size());
        assertEquals(1000, sessions.get(0).odometerKm());
        assertEquals(1042, sessions.get(1).odometerKm());
    }
}
