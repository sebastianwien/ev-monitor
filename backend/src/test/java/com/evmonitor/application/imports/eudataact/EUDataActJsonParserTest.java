package com.evmonitor.application.imports.eudataact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EUDataActJsonParserTest {

    private EUDataActJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new EUDataActJsonParser(new ObjectMapper());
    }

    private InputStream sampleJson() {
        return getClass().getClassLoader().getResourceAsStream("eudataact/WVWZZZ-ID7_20251213015510.json");
    }

    @Test
    void parsesVin() throws Exception {
        EUDataActParseResult result = parser.parse(sampleJson());
        assertEquals("WVWZZZ-ID7", result.vin());
    }

    @Test
    void detectsThreeSessions() throws Exception {
        EUDataActParseResult result = parser.parse(sampleJson());
        assertEquals(3, result.sessions().size());
    }

    @Test
    void session1_isAcWithCorrectSocAndDuration() throws Exception {
        EUDataActSession s = parser.parse(sampleJson()).sessions().get(0);

        assertEquals("AC", s.chargeType());
        assertEquals(74, s.socBefore());
        assertEquals(100, s.socAfter());
        assertTrue(s.durationMin() >= 110 && s.durationMin() <= 120, "Expected ~115 min, got: " + s.durationMin());
    }

    @Test
    void session1_kwhCalculatedViaIntegration() throws Exception {
        EUDataActSession s = parser.parse(sampleJson()).sessions().get(0);
        // AC session ~115 min at ~10 kW → expect 15-21 kWh
        assertNotNull(s.calculatedKwh());
        assertTrue(s.calculatedKwh() >= 15.0 && s.calculatedKwh() <= 21.0,
                "Expected 15-21 kWh, got: " + s.calculatedKwh());
    }

    @Test
    void session2_isDcFastCharge() throws Exception {
        EUDataActSession s = parser.parse(sampleJson()).sessions().get(1);

        assertEquals("DC", s.chargeType());
        assertEquals(12, s.socBefore());
        assertTrue(s.socAfter() >= 65 && s.socAfter() <= 72, "Expected ~68% SoC after, got: " + s.socAfter());
        // DC fast charge, max ~189 kW
        assertTrue(s.maxChargingPowerKw() >= 100.0, "Expected high DC power, got: " + s.maxChargingPowerKw());
    }

    @Test
    void session2_kwhCalculatedViaIntegration() throws Exception {
        EUDataActSession s = parser.parse(sampleJson()).sessions().get(1);
        // DC session ~20 min, up to 189 kW → expect 40-55 kWh
        assertNotNull(s.calculatedKwh());
        assertTrue(s.calculatedKwh() >= 40.0 && s.calculatedKwh() <= 55.0,
                "Expected 40-55 kWh, got: " + s.calculatedKwh());
    }

    @Test
    void session3_isDcWithCorrectSoc() throws Exception {
        EUDataActSession s = parser.parse(sampleJson()).sessions().get(2);

        assertEquals("DC", s.chargeType());
        assertEquals(61, s.socBefore());
        assertEquals(76, s.socAfter());
    }

    @Test
    void session3_kwhCalculatedViaIntegration() throws Exception {
        EUDataActSession s = parser.parse(sampleJson()).sessions().get(2);
        // DC session ~22 min at ~35 kW → expect 10-15 kWh
        assertNotNull(s.calculatedKwh());
        assertTrue(s.calculatedKwh() >= 10.0 && s.calculatedKwh() <= 15.0,
                "Expected 10-15 kWh, got: " + s.calculatedKwh());
    }

    @Test
    void sessionsHaveOdometerKm() throws Exception {
        List<EUDataActSession> sessions = parser.parse(sampleJson()).sessions();
        sessions.forEach(s -> assertNotNull(s.odometerKm(), "odometerKm should not be null"));
        // Session 1 at ~1951 km
        assertEquals(1951, sessions.get(0).odometerKm());
    }

    @Test
    void sessionsHaveTemperature() throws Exception {
        // temperatureOutsideVehicle is in Kelvin (e.g. 280.15 K = 7.0 °C)
        List<EUDataActSession> sessions = parser.parse(sampleJson()).sessions();
        sessions.stream()
                .filter(s -> s.temperatureCelsius() != null)
                .forEach(s -> assertTrue(s.temperatureCelsius() > -50 && s.temperatureCelsius() < 60,
                        "Temperature out of range: " + s.temperatureCelsius()));
    }

    @Test
    void sessionsAreSortedChronologically() throws Exception {
        List<EUDataActSession> sessions = parser.parse(sampleJson()).sessions();
        for (int i = 1; i < sessions.size(); i++) {
            assertTrue(sessions.get(i).startedAt().isAfter(sessions.get(i - 1).startedAt()));
        }
    }
}
