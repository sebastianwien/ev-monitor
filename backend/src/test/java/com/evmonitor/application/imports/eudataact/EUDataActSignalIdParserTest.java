package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MEB-Variante des EU-Data-Act-Exports (z.B. ID.3): enthaelt keine sprechenden Feldnamen,
 * sondern rohe Signal-IDs. Ladevorgaenge werden aus dem SoC-Verlauf rekonstruiert.
 */
class EUDataActSignalIdParserTest {

    private EUDataActJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new EUDataActJsonParser(new ObjectMapper());
    }

    /** Fixture liegt als ZIP vor (23 MB entpackt), anonymisiert. */
    private InputStream signalIdJson() throws Exception {
        try (ZipInputStream zip = new ZipInputStream(
                getClass().getClassLoader().getResourceAsStream("eudataact/MEB_signal_ids.zip"))) {
            zip.getNextEntry();
            return new ByteArrayInputStream(zip.readAllBytes());
        }
    }

    @Test
    void parsesVin() throws Exception {
        assertEquals("WVWZZZED0TESTVIN0", parser.parse(signalIdJson()).vin());
    }

    @Test
    void detectsNineChargingSessions() throws Exception {
        assertEquals(9, parser.parse(signalIdJson()).sessions().size());
    }

    @Test
    void ignoresZeroSocDropouts() throws Exception {
        // Die Rohreihe enthaelt 3x SoC=0.0 (Offline-Platzhalter mitten in fallender Kurve).
        // Werden sie mitgerechnet, entstehen Geister-Ladungen 0% -> 79% in zwei Minuten.
        List<EUDataActSession> sessions = parser.parse(signalIdJson()).sessions();
        sessions.forEach(s -> assertTrue(s.socBefore() > 0,
                "Geister-Session aus 0.0-Dropout bei " + s.startedAt()));
    }

    @Test
    void splitsSessionsByOdometerChange() throws Exception {
        // 20.05.: SoC faellt noch bei 12:17 (Wagen faehrt, km 4377 -> 4379), Ladung startet erst 12:30.
        // Ohne Kilometerstand-Split wuerde die Session 13 Minuten zu frueh beginnen.
        EUDataActSession s = parser.parse(signalIdJson()).sessions().get(3);

        assertEquals(54, s.socBefore());
        assertEquals(70, s.socAfter()); // 69.5 gerundet
        assertTrue(s.durationMin() >= 9 && s.durationMin() <= 13,
                "Erwartet ~11 min, war: " + s.durationMin());
    }

    @Test
    void sessionsHaveSocAndOdometer() throws Exception {
        List<EUDataActSession> sessions = parser.parse(signalIdJson()).sessions();
        sessions.forEach(s -> {
            assertNotNull(s.socBefore());
            assertNotNull(s.socAfter());
            assertNotNull(s.odometerKm());
            assertTrue(s.socAfter() > s.socBefore());
        });
        assertEquals(3929, sessions.get(0).odometerKm());
    }

    @Test
    void exposesExactSocDeltaForKwhCalculation() throws Exception {
        // Der Parser kennt die Batteriekapazitaet nicht - kWh rechnet der Service.
        // Er braucht dafuer den ungerundeten Delta (43.0 -> 91.5 = 48.5, nicht 49).
        EUDataActSession s = parser.parse(signalIdJson()).sessions().get(0);

        assertNull(s.calculatedKwh(), "Ohne Kapazitaet kann der Parser keine kWh liefern");
        assertNotNull(s.socDeltaPct());
        assertEquals(48.5, s.socDeltaPct(), 0.01);
    }

    @Test
    void hasNoChargeTypeOrPower() throws Exception {
        // Diese Variante liefert kein Leistungssignal - nicht raten.
        List<EUDataActSession> sessions = parser.parse(signalIdJson()).sessions();
        sessions.forEach(s -> {
            assertNull(s.chargeType());
            assertNull(s.maxChargingPowerKw());
        });
    }

    @Test
    void sessionsAreSortedChronologically() throws Exception {
        List<EUDataActSession> sessions = parser.parse(signalIdJson()).sessions();
        for (int i = 1; i < sessions.size(); i++) {
            assertTrue(sessions.get(i).startedAt().isAfter(sessions.get(i - 1).startedAt()));
        }
    }

    @Test
    void rejectsUnknownFormatInsteadOfImportingNothing() {
        String json = """
                {"vin":"X","Data":[{"dataFieldName":"999999","value":"1","timestampUtc":"2026-01-01T00:00:00Z"}]}
                """;
        InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> parser.parse(in));
    }
}
