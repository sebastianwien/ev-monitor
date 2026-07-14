package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zweiter, unabhaengiger ID.3 (anderer Halter, anderer Export-Zeitpunkt). Belegt, dass die
 * Signal-Identifikation fahrzeuguebergreifend traegt - und nicht auf die erste Datei getunt ist.
 * <p>
 * Diese Datei hat zwei Eigenheiten, die die erste nicht hatte: die SoC-Zeitreihe endet zwei Tage
 * vor dem Export (das Auto stand), und der Kilometerstand enthaelt Ungueltig-Marker (1048574).
 */
class EUDataActSecondVehicleTest {

    private EUDataActJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new EUDataActJsonParser(new ObjectMapper());
    }

    private InputStream json() throws Exception {
        try (ZipInputStream zip = new ZipInputStream(
                getClass().getClassLoader().getResourceAsStream("eudataact/MEB_signal_ids_car2.zip"))) {
            zip.getNextEntry();
            return new ByteArrayInputStream(zip.readAllBytes());
        }
    }

    @Test
    void isRecognisedAsMebFormat() throws Exception {
        EUDataActParseResult result = parser.parse(json());

        assertEquals("WVWZZZED0TESTVIN2", result.vin());
        assertFalse(result.sessions().isEmpty(), "Ladevorgaenge muessen erkannt werden");
    }

    @Test
    void sessionsArePlausible() throws Exception {
        List<EUDataActSession> sessions = parser.parse(json()).sessions();

        sessions.forEach(s -> {
            assertTrue(s.socBefore() > 0 && s.socBefore() <= 100, "SoC vorher: " + s.socBefore());
            assertTrue(s.socAfter() > s.socBefore(), "SoC muss steigen");
            assertTrue(s.socAfter() <= 100, "SoC nachher: " + s.socAfter());
            assertNotNull(s.odometerKm());
            assertTrue(s.odometerKm() > 30_000 && s.odometerKm() < 40_000,
                    "Kilometerstand ausserhalb des Datei-Bereichs (Ungueltig-Marker?): " + s.odometerKm());
        });
    }

    @Test
    void sessionsAreSortedChronologically() throws Exception {
        List<EUDataActSession> sessions = parser.parse(json()).sessions();
        for (int i = 1; i < sessions.size(); i++) {
            assertTrue(sessions.get(i).startedAt().isAfter(sessions.get(i - 1).startedAt()));
        }
    }
}
