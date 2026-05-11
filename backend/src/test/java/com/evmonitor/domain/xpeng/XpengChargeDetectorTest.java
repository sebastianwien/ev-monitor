package com.evmonitor.domain.xpeng;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XpengChargeDetectorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 4, 22, 18, 0, 0);

    @Test
    void emitsNothingWhenNeverCharging() {
        XpengChargeDetector detector = new XpengChargeDetector();
        List<DetectedChargingSession> sessions = detect(detector, List.of(
                parked(0, "80", null),
                parked(5, "80", null),
                parked(10, "80", null)));
        assertTrue(sessions.isEmpty());
    }

    @Test
    void detectsSimpleAcChargingSession() {
        XpengChargeDetector detector = new XpengChargeDetector();
        // 11 kW for 60s = 11 * 60/3600 = 0.1833 kWh
        List<DetectedChargingSession> sessions = detect(detector, List.of(
                parked(0, "50", null),
                parked(5, "50", "11"),
                parked(10, "51", "11"),
                parked(15, "51", "11"),
                parked(20, "52", "11"),
                parked(25, "52", "11"),
                parked(30, "53", "11"),
                parked(35, "53", "11"),
                parked(40, "54", "11"),
                parked(45, "54", "11"),
                parked(50, "55", "11"),
                parked(55, "55", "11"),
                parked(60, "56", "11"),
                parked(65, "56", null),
                parked(120, "56", null)));
        assertEquals(1, sessions.size());
        DetectedChargingSession s = sessions.get(0);
        assertEquals(T0.plusSeconds(5), s.startedAt());
        assertEquals(0, new BigDecimal("50").compareTo(s.socStart()));
        assertEquals(0, new BigDecimal("56").compareTo(s.socEnd()));
        assertEquals("AC", s.chargingType(), "11 kW is AC");
        assertEquals(0, new BigDecimal("11").compareTo(s.maxPowerKw()));
    }

    @Test
    void detectsDcFastChargingByMaxPower() {
        XpengChargeDetector detector = new XpengChargeDetector();
        List<DetectedChargingSession> sessions = detect(detector, List.of(
                parked(0, "20", null),
                parked(5, "20", "150"),
                parked(10, "30", "150"),
                parked(15, "40", "120"),
                parked(20, "50", "80"),
                parked(80, "70", null)));
        assertEquals(1, sessions.size());
        assertEquals("DC", sessions.get(0).chargingType(), "150 kW peak means DC fast");
    }

    @Test
    void doesNotStartChargingWhileDriving() {
        XpengChargeDetector detector = new XpengChargeDetector();
        // Regen braking would show negative or positive chargePower while in gear D;
        // we only count it as charging when parked.
        List<DetectedChargingSession> sessions = detect(detector, List.of(
                driving(0, "80", "5"),
                driving(5, "80", "10"),
                driving(10, "80", "5"),
                parked(15, "80", null)));
        assertTrue(sessions.isEmpty(), "no charging session while car is moving in D");
    }

    @Test
    void tolerates60sGapBeforeEndingSession() {
        XpengChargeDetector detector = new XpengChargeDetector();
        // 22 kW * 60s = 0.367 kWh up to gap, then more after.
        // Brief 30s drop to 0 kW shouldn't end the session (e.g. CP renegotiation).
        List<XpengTelematicsRow> rows = new java.util.ArrayList<>();
        for (int t = 0; t <= 60; t += 5)  rows.add(parked(t,  "50", "22"));
        for (int t = 65; t <= 90; t += 5) rows.add(parked(t,  "52", "0"));   // 30s gap
        for (int t = 95; t <= 150; t += 5) rows.add(parked(t, "55", "22"));
        rows.add(parked(300, "60", null));
        List<DetectedChargingSession> sessions = detect(detector, rows);
        assertEquals(1, sessions.size(),
                "brief gaps under 60s of zero power should keep session open");
    }

    // -- helpers --

    private List<DetectedChargingSession> detect(XpengChargeDetector detector, List<XpengTelematicsRow> rows) {
        List<DetectedChargingSession> emitted = new ArrayList<>();
        for (XpengTelematicsRow row : rows) {
            detector.consume(row).ifPresent(emitted::add);
        }
        detector.finish().ifPresent(emitted::add);
        return emitted;
    }

    private XpengTelematicsRow parked(int sec, String soc, String chargePower) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                BigDecimal.ZERO, 4,
                new BigDecimal("13508"), new BigDecimal(soc),
                null, null,
                chargePower == null ? null : new BigDecimal(chargePower),
                null, null);
    }

    private XpengTelematicsRow driving(int sec, String soc, String chargePower) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                new BigDecimal("50"), 1,
                new BigDecimal("13508"), new BigDecimal(soc),
                null, null,
                chargePower == null ? null : new BigDecimal(chargePower),
                null, null);
    }
}
