package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Historien-Export ("Request File" im Portal): VW liefert fertige Ladevorgaenge als
 * {@code chargingSession.[n].*}-Records am Ende einer sehr grossen Datei. Die Records
 * tragen ihre Zeitstempel als Werte - das {@code timestampUtc} der Eintraege ist dort
 * unbrauchbar (Epoch-Muell aus 1970).
 */
class EUDataActHistoricalExportTest {

    private static final String MILEAGE_SIGNAL = "180876";

    private EUDataActJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new EUDataActJsonParser(new ObjectMapper());
    }

    private record Entry(String field, String value, String timestamp) {}

    private static Entry session(int n, String field, String value) {
        return new Entry("chargingSession.[" + n + "]." + field, value, "1970-01-21T15:16:13.514Z");
    }

    private static List<Entry> acSession(int n) {
        return List.of(
                session(n, "sessionId", "6ed67ff3-7d8f-40dc-a11e-d78b11c47525"),
                session(n, "chargeType", "AC"),
                session(n, "connectionTimestamp", "2026-09-14T08:19:17Z"),
                session(n, "startChargingTimestamp", "2026-09-14T08:25:02Z"),
                session(n, "stopChargingTimestamp", "2026-09-14T12:19:04Z"),
                session(n, "disconnectionTimestamp", "2026-09-14T12:19:12Z"),
                session(n, "activeChargingTime", "5704"),
                session(n, "startSoc", "67"),
                session(n, "endSoc", "71"),
                session(n, "deltaSoc", "4"),
                session(n, "totalEnergyCharged", "2.0"),
                session(n, "averageChargePower", "1.3"),
                session(n, "peakChargePower", "3.9"),
                session(n, "chargeMode.[1]", "immediatelyProfile"));
    }

    private static List<Entry> dcSession(int n) {
        return List.of(
                session(n, "sessionId", "f1da1fdd-83b3-4619-9cff-3277dbd191cd"),
                session(n, "chargeType", "DC"),
                session(n, "startChargingTimestamp", "2026-08-02T10:00:00Z"),
                session(n, "stopChargingTimestamp", "2026-08-02T10:31:00Z"),
                session(n, "activeChargingTime", "1860"),
                session(n, "startSoc", "20"),
                session(n, "endSoc", "80"),
                session(n, "totalEnergyCharged", "49.5"),
                session(n, "peakChargePower", "121.0"));
    }

    private static List<Entry> emptySession(int n) {
        return List.of(
                session(n, "sessionId", "00000000-0000-0000-0000-000000000000"),
                session(n, "chargeType", "AC"),
                session(n, "startChargingTimestamp", "2026-08-05T18:00:00Z"),
                session(n, "stopChargingTimestamp", "2026-08-05T18:00:30Z"),
                session(n, "activeChargingTime", "0"),
                session(n, "startSoc", "55"),
                session(n, "endSoc", "55"),
                session(n, "totalEnergyCharged", "0.0"));
    }

    /** Echte Exporte: Telemetrie zuerst (Werte mit Einheit/Leerzeichen), Session-Records ganz am Ende. */
    private List<Entry> historicalExport() {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(MILEAGE_SIGNAL, "21714 ", "2026-07-02 17:24:14"));
        entries.add(new Entry(MILEAGE_SIGNAL, "25127 ", "2026-08-01 12:18:08"));
        entries.add(new Entry(MILEAGE_SIGNAL, "27832 ", "2026-09-14T07:50:00Z"));
        entries.add(new Entry(MILEAGE_SIGNAL, "27901 ", "2026-09-16T07:50:00Z"));
        for (int i = 0; i < 200; i++) {
            entries.add(new Entry("545620", "0.0 Unit_KiloMeterPerHour", "2026-09-14T0" + (i % 10) + ":00:00Z"));
            entries.add(new Entry("546774", "8.7 Unit_Amper", "2026-09-14T0" + (i % 10) + ":00:00Z"));
        }
        entries.addAll(dcSession(1));
        entries.addAll(emptySession(2));
        entries.addAll(acSession(3));
        return entries;
    }

    private ByteArrayInputStream export(List<Entry> entries) {
        String data = entries.stream()
                .map(e -> """
                        {"dataFieldName":"%s","value":"%s","timestampUtc":"%s"}"""
                        .formatted(e.field(), e.value(), e.timestamp()))
                .collect(Collectors.joining(","));
        return new ByteArrayInputStream(
                """
                {"vin":"TMBTESTVIN0001325","userId":"u","Data":[%s]}""".formatted(data)
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesVin() throws Exception {
        assertEquals("TMBTESTVIN0001325", parser.parse(export(historicalExport())).vin());
    }

    @Test
    void takesSessionsFromRecords_skipsSessionsWithoutEnergy() throws Exception {
        List<EUDataActSession> sessions = parser.parse(export(historicalExport())).sessions();
        assertEquals(2, sessions.size());
    }

    @Test
    void sessionsAreOrderedByStart() throws Exception {
        List<EUDataActSession> sessions = parser.parse(export(historicalExport())).sessions();
        assertTrue(sessions.get(0).startedAt().isBefore(sessions.get(1).startedAt()));
    }

    @Test
    void acSession_mapsAllFieldsFromRecord() throws Exception {
        EUDataActSession s = parser.parse(export(historicalExport())).sessions().get(1);
        assertEquals(OffsetDateTime.parse("2026-09-14T08:25:02Z"), s.startedAt());
        assertEquals(OffsetDateTime.parse("2026-09-14T12:19:04Z"), s.endedAt());
        assertEquals(95, s.durationMin(), "activeChargingTime 5704 s -> 95 min");
        assertEquals(67, s.socBefore());
        assertEquals(71, s.socAfter());
        assertEquals("AC", s.chargeType());
        assertEquals(3.9, s.maxChargingPowerKw(), 0.001);
        assertEquals(2.0, s.calculatedKwh(), 0.001, "kWh kommen aus dem Record, nicht aus einer Integration");
        assertNull(s.temperatureCelsius());
    }

    @Test
    void dcSession_isMapped() throws Exception {
        EUDataActSession s = parser.parse(export(historicalExport())).sessions().get(0);
        assertEquals("DC", s.chargeType());
        assertEquals(49.5, s.calculatedKwh(), 0.001);
        assertEquals(121.0, s.maxChargingPowerKw(), 0.001);
        assertEquals(31, s.durationMin());
    }

    @Test
    void odometerComesFromLastMileageReadingBeforeStart() throws Exception {
        List<EUDataActSession> sessions = parser.parse(export(historicalExport())).sessions();
        assertEquals(27832, sessions.get(1).odometerKm(), "letzter Kilometerstand vor 14.09. 08:25");
        assertEquals(25127, sessions.get(0).odometerKm(), "letzter Kilometerstand vor 02.08. 10:00 ist der vom 01.08.");
    }

    @Test
    void recordsWinOverCurveDetectionWhenBothArePresent() throws Exception {
        // Ein Export mit Records UND einer SoC-Zeitreihe darf nicht doppelt erkennen
        List<Entry> entries = new ArrayList<>(historicalExport());
        double soc = 20;
        for (int i = 0; i < 60; i++) {
            entries.add(new Entry("180886", String.valueOf(soc), OffsetDateTime.parse("2026-08-02T10:00:00Z").plusMinutes(i).toString()));
            soc += 1.0;
        }
        assertEquals(2, parser.parse(export(entries)).sessions().size());
    }

    @Test
    void tooManyDistinctFieldsIsRejected_notBuffered() {
        // Pass 1 haelt die Feldnamen im Speicher - eine Datei mit Millionen verschiedener
        // Feldnamen darf nicht den Heap fressen, sondern muss frueh abgelehnt werden
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i <= EUDataActJsonParser.MAX_DISTINCT_FIELDS; i++) {
            entries.add(new Entry("f" + i, "1", "2026-01-01T00:00:00Z"));
        }
        assertThrows(IllegalArgumentException.class, () -> parser.parse(export(entries)));
    }

    @Test
    void recordWithoutStartTimestampIsSkipped() throws Exception {
        List<Entry> entries = new ArrayList<>(historicalExport());
        entries.add(session(4, "totalEnergyCharged", "12.0"));
        entries.add(session(4, "chargeType", "AC"));
        assertEquals(2, parser.parse(export(entries)).sessions().size());
    }
}
