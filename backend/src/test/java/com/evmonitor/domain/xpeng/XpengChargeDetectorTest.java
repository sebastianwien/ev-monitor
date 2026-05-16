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
    void ignoresChargePowerSpikeInsideRealAcSession() {
        // Reale Beobachtung aus XPeng-File: 1638.3 kW Sensor-Sentinel taucht
        // mitten in 10-kW-Wallbox-Session auf. Ohne Cap waere die Session
        // faelschlich als DC klassifiziert + Phantom-kWh aufaddiert.
        XpengChargeDetector detector = new XpengChargeDetector();
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(parked(0, "50", null));
        for (int t = 5; t <= 30; t += 5) rows.add(parked(t, "50", "10"));
        rows.add(parked(35, "50", "1638.3"));
        for (int t = 40; t <= 120; t += 5) rows.add(parked(t, "55", "10"));
        rows.add(parked(180, "55", null));

        List<DetectedChargingSession> sessions = detect(detector, rows);

        assertEquals(1, sessions.size());
        DetectedChargingSession s = sessions.get(0);
        assertEquals("AC", s.chargingType(),
                "Sensor-Spike darf nicht zu DC-Klassifikation fuehren");
        assertTrue(s.maxPowerKw().compareTo(new BigDecimal("400")) <= 0,
                "maxPowerKw muss durch Sanity-Cap begrenzt sein, war " + s.maxPowerKw());
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

    @Test
    void aggregatesBatteryTemperatureExtras() {
        XpengChargeDetector detector = new XpengChargeDetector();
        // 11 kW Session, batt-temp variiert ueber Zeit. Aggregator soll min/max/avg
        // in telemetry_extras.battery liefern. Dauer >60s damit Session ueber
        // MIN_SESSION_KWH (0.05) liegt: 11kW * 60/3600 = 0.183 kWh.
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(parked(0, "50", null));
        for (int t = 5; t <= 60; t += 5) {
            // Temp 15-25 abwechselnd, gleicher Mittelwert
            String tMax = t < 30 ? "18" : "25";
            String tMin = t < 30 ? "10" : "15";
            rows.add(parkedWithTemp(t, "5" + (t % 5), "11", tMax, tMin));
        }
        rows.add(parked(150, "55", null));

        List<DetectedChargingSession> sessions = detect(detector, rows);
        assertEquals(1, sessions.size());
        java.util.Map<String, Object> extras = sessions.get(0).telemetryExtras();
        assertNotNull(extras, "telemetryExtras must be populated");
        assertEquals("xpeng", extras.get("source"));
        assertEquals(1, extras.get("schema_version"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> battery = (java.util.Map<String, Object>) extras.get("battery");
        assertNotNull(battery);
        assertEquals(new BigDecimal("25.0"), battery.get("pack_temp_max_c"));
        assertEquals(new BigDecimal("10.0"), battery.get("pack_temp_min_c"));
        assertNotNull(battery.get("pack_temp_avg_c"));
    }

    @Test
    void integratesKwhAtVehicleFromBatteryVoltageAndCurrent() {
        // 11 kW AC am OBC parallel zu DC-Pack 400 V x -26.25 A = 10.5 kW Pack-Input.
        // (XPeng-Konvention: Laden = negativer Strom, deshalb -(U x I) ergibt positive Leistung.)
        // 60 s lang: brutto 11*60/3600=0.1833 kWh, netto 10.5*60/3600=0.175 kWh, Ratio ~95.5%.
        XpengChargeDetector detector = new XpengChargeDetector();
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(parked(0, "50", null));
        for (int t = 5; t <= 60; t += 5) {
            rows.add(parkedWithBattery(t, "50", "11", "400", "-26.25"));
        }
        rows.add(parked(120, "55", null));

        List<DetectedChargingSession> sessions = detect(detector, rows);

        assertEquals(1, sessions.size());
        DetectedChargingSession s = sessions.get(0);
        assertNotNull(s.kwhAtVehicle(), "kwhAtVehicle muss aus U/I berechnet sein");
        BigDecimal brutto = s.kwhCharged();
        BigDecimal netto = s.kwhAtVehicle();
        BigDecimal ratio = netto.divide(brutto, 4, java.math.RoundingMode.HALF_UP);
        assertTrue(ratio.compareTo(new BigDecimal("0.93")) > 0
                && ratio.compareTo(new BigDecimal("0.97")) < 0,
                "Netto/Brutto sollte ~95% sein, war " + ratio + " (netto=" + netto + ", brutto=" + brutto + ")");
    }

    @Test
    void leavesKwhAtVehicleNullWhenNoBatterySignals() {
        // Fallback: wenn battVolt/battCurr durchgaengig fehlen, bleibt kwhAtVehicle null.
        // Konsument muss dann auf SoC-Delta-Schaetzung zurueckfallen.
        XpengChargeDetector detector = new XpengChargeDetector();
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(parked(0, "50", null));
        for (int t = 5; t <= 60; t += 5) rows.add(parked(t, "50", "11"));
        rows.add(parked(120, "55", null));

        List<DetectedChargingSession> sessions = detect(detector, rows);

        assertEquals(1, sessions.size());
        assertNull(sessions.get(0).kwhAtVehicle(),
                "ohne U/I-Samples muss kwhAtVehicle null sein, nicht 0");
    }

    @Test
    void skipsImplausibleBatterySamplesButKeepsValidOnes() {
        // Sensor-Glitch: ein Sample meldet I=-9999 A (=4 MW Pack, absurd).
        // Plausi-Cap muss das Sample verwerfen, restliche Session weiter integrieren.
        XpengChargeDetector detector = new XpengChargeDetector();
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(parked(0, "50", null));
        for (int t = 5; t <= 30; t += 5) rows.add(parkedWithBattery(t, "50", "11", "400", "-26"));
        rows.add(parkedWithBattery(35, "50", "11", "400", "-9999"));  // Glitch
        for (int t = 40; t <= 90; t += 5) rows.add(parkedWithBattery(t, "55", "11", "400", "-26"));
        rows.add(parked(150, "55", null));

        List<DetectedChargingSession> sessions = detect(detector, rows);

        assertEquals(1, sessions.size());
        BigDecimal netto = sessions.get(0).kwhAtVehicle();
        assertNotNull(netto);
        // Wenn der Glitch durchschluepfen wuerde: ~17 kWh in einem 5s-Sample. Hier muss netto realistisch <0.4 kWh bleiben.
        assertTrue(netto.compareTo(new BigDecimal("0.5")) < 0,
                "Glitch-Sample muss verworfen sein, netto war " + netto);
    }

    @Test
    void returnsNullWhenBatteryCurrentSignSuggestsSensorInversion() {
        // Sanity: wenn waehrend chrgpwr > 0 die Pack-Stromrichtung durchgaengig
        // positiv ist (Entladung), wuerde -(U x I) systematisch negativ.
        // Statt unsinnigen Negativwert speichern wir null und ueberlassen es dem
        // SoC-Delta-Fallback.
        XpengChargeDetector detector = new XpengChargeDetector();
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(parked(0, "50", null));
        for (int t = 5; t <= 60; t += 5) {
            rows.add(parkedWithBattery(t, "50", "11", "400", "+26.0"));  // falsches Vorzeichen
        }
        rows.add(parked(120, "55", null));

        List<DetectedChargingSession> sessions = detect(detector, rows);

        assertEquals(1, sessions.size());
        assertNull(sessions.get(0).kwhAtVehicle(),
                "negative Netto-Energie ist physikalisch Unsinn waehrend Laden - sollte null sein");
    }

    @Test
    void returnsNullWhenPackEnergyImplausiblyLowerThanGross() {
        // Sanity-Fenster: real liegt netto zwischen 70 % (sehr ineffizient) und 105 %
        // (kleiner Messoffset) von brutto. Wenn z.B. ein Pack-Sensor halbe Werte
        // liefert (40 % netto / brutto), ist das ein Defekt - lieber null als Luege.
        // Hier simulieren wir 1 V Spannung waehrend "11 kW AC laedt" - U x I ergibt
        // viel zu wenig kWh - nein, U muss valide sein (>=100V), also tricksen wir
        // mit niedrigem Strom: 100 V x -5 A = 0.5 kW Pack-Input bei 11 kW AC = 4.5 %.
        XpengChargeDetector detector = new XpengChargeDetector();
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(parked(0, "50", null));
        for (int t = 5; t <= 60; t += 5) {
            rows.add(parkedWithBattery(t, "50", "11", "100", "-5"));
        }
        rows.add(parked(120, "55", null));

        List<DetectedChargingSession> sessions = detect(detector, rows);

        assertEquals(1, sessions.size());
        assertNull(sessions.get(0).kwhAtVehicle(),
                "netto < 70 % von brutto deutet auf defekten Sensor hin - sollte null sein");
    }

    @Test
    void integratesDcFastChargingWithHighPackCurrent() {
        // DC-Schnellladen 150 kW: chrgpwr = 150 kW (DC direkt im OBC-Bypass),
        // Pack 400 V x -360 A = 144 kW (4 % Verlust am HV-Inlet/Schuetz). Realistisch.
        // Stellt sicher dass MAX_PACK_POWER_KW (400) und MAX_BATT_CURRENT_A (1000)
        // grosszuegig genug sind fuer Real-DC.
        XpengChargeDetector detector = new XpengChargeDetector();
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(parked(0, "20", null));
        for (int t = 5; t <= 60; t += 5) {
            rows.add(parkedWithBattery(t, "20", "150", "400", "-360"));
        }
        rows.add(parked(120, "30", null));

        List<DetectedChargingSession> sessions = detect(detector, rows);

        assertEquals(1, sessions.size());
        DetectedChargingSession s = sessions.get(0);
        assertEquals("DC", s.chargingType());
        assertNotNull(s.kwhAtVehicle(), "DC-Pack-Strom muss durch Plausi-Cap durchgehen");
        BigDecimal ratio = s.kwhAtVehicle().divide(s.kwhCharged(), 4, java.math.RoundingMode.HALF_UP);
        assertTrue(ratio.compareTo(new BigDecimal("0.92")) > 0
                && ratio.compareTo(new BigDecimal("1.00")) < 0,
                "DC-Netto/Brutto sollte 92-100 % sein, war " + ratio);
    }

    private XpengTelematicsRow parkedWithTemp(int sec, String soc, String chargePower,
                                                String tempMax, String tempMin) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                BigDecimal.ZERO, 4,
                new BigDecimal("13508"), new BigDecimal(soc),
                null, null,
                chargePower == null ? null : new BigDecimal(chargePower),
                new BigDecimal(tempMax), new BigDecimal(tempMin), java.util.Map.of());
    }

    private XpengTelematicsRow parked(int sec, String soc, String chargePower) {
        return parkedWithBattery(sec, soc, chargePower, null, null);
    }

    private XpengTelematicsRow parkedWithBattery(int sec, String soc, String chargePower,
                                                  String battVolt, String battCurrent) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                BigDecimal.ZERO, 4,
                new BigDecimal("13508"), new BigDecimal(soc),
                battVolt == null ? null : new BigDecimal(battVolt),
                battCurrent == null ? null : new BigDecimal(battCurrent),
                chargePower == null ? null : new BigDecimal(chargePower),
                null, null, java.util.Map.of());
    }

    private XpengTelematicsRow driving(int sec, String soc, String chargePower) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                new BigDecimal("50"), 1,
                new BigDecimal("13508"), new BigDecimal(soc),
                null, null,
                chargePower == null ? null : new BigDecimal(chargePower),
                null, null, java.util.Map.of());
    }
}
