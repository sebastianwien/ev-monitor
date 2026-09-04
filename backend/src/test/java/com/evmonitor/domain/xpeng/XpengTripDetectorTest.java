package com.evmonitor.domain.xpeng;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XpengTripDetectorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 4, 22, 10, 0, 0);

    @Test
    void emitsNothingWhenAlwaysParked() {
        XpengTripDetector detector = new XpengTripDetector();
        List<XpengTelematicsRow> rows = sequence(
                parked(0, 100, "13508", "80"),
                parked(5, 100, "13508", "80"),
                parked(10, 100, "13508", "80"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertTrue(trips.isEmpty(), "no trip expected when always in P");
    }

    @Test
    void detectsSingleTripFromParkToDriveToPark() {
        XpengTripDetector detector = new XpengTripDetector();
        List<XpengTelematicsRow> rows = sequence(
                parked(0, 100, "13508.0", "80"),
                driving(5, 50, "13508.5", "79"),
                driving(10, 60, "13509.0", "79"),
                driving(15, 40, "13509.5", "78"),
                parked(20, 0, "13510.0", "78"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertEquals(1, trips.size());
        DetectedTrip trip = trips.get(0);
        assertEquals(T0.plusSeconds(5), trip.startedAt());
        assertEquals(T0.plusSeconds(20), trip.endedAt());
        assertEquals(0, new BigDecimal("2.0").compareTo(trip.distanceKm()),
                "odometer-derived distance");
        assertEquals(0, new BigDecimal("80").compareTo(trip.socStart()));
        assertEquals(0, new BigDecimal("78").compareTo(trip.socEnd()));
        // Speed aggregation over the three driving samples 50/60/40 (parked row excluded)
        assertEquals(0, new BigDecimal("50.00").compareTo(trip.avgSpeedKmh()));
        assertEquals(0, new BigDecimal("60").compareTo(trip.maxSpeedKmh()));
    }

    @Test
    void emitsNullSpeedWhenNoSamplesAvailable() {
        // Trip with valid odometer-distance but vehSpeedKmh always null - parser
        // could not resolve esp_vehspd column. Expected: null, not 0, so the boundary
        // doesn't confuse "no data" with "stood still".
        XpengTripDetector detector = new XpengTripDetector();
        List<XpengTelematicsRow> rows = sequence(
                parked(0, 0, "100.0", "80"),
                drivingNoSpeed(5, "100.5", "80"),
                drivingNoSpeed(10, "101.0", "79"),
                parked(15, 0, "101.5", "79"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertEquals(1, trips.size());
        DetectedTrip trip = trips.get(0);
        assertNull(trip.avgSpeedKmh());
        assertNull(trip.maxSpeedKmh());
    }

    private XpengTelematicsRow drivingNoSpeed(int sec, String odo, String soc) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                null, 1,
                new BigDecimal(odo), new BigDecimal(soc),
                null, null, null, null, null, java.util.Map.of());
    }

    @Test
    void detectsMultipleTripsSeparatedByPark() {
        XpengTripDetector detector = new XpengTripDetector();
        List<XpengTelematicsRow> rows = sequence(
                driving(0, 50, "100.0", "90"),
                parked(5, 0, "101.0", "89"),
                parked(10, 0, "101.0", "89"),
                driving(15, 60, "101.0", "89"),
                parked(20, 0, "102.0", "88"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertEquals(2, trips.size());
        assertEquals(0, new BigDecimal("1.0").compareTo(trips.get(0).distanceKm()));
        assertEquals(0, new BigDecimal("1.0").compareTo(trips.get(1).distanceKm()));
    }

    @Test
    void discardsTripWithDistanceBelowThreshold() {
        XpengTripDetector detector = new XpengTripDetector();
        List<XpengTelematicsRow> rows = sequence(
                parked(0, 0, "100.00", "80"),
                driving(5, 5, "100.05", "80"),
                parked(10, 0, "100.05", "80"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertTrue(trips.isEmpty(), "0.05 km is below 0.1 km minimum, should be discarded");
    }

    @Test
    void integratesEnergyOverTrip() {
        XpengTripDetector detector = new XpengTripDetector();
        // 400V * 100A = 40 kW for 15s (3 samples at 5s each) = 40 * 15/3600 = 0.1667 kWh
        // P=400*100 = 40000 W, time = 15s, energy = 40000 * 15 / 3600000 kWh = 0.1667 kWh
        List<XpengTelematicsRow> rows = sequence(
                parked(0, 0, "100.0", "90"),
                drivingWithPower(5, 50, "100.5", "90", "400", "100"),
                drivingWithPower(10, 50, "101.0", "89", "400", "100"),
                drivingWithPower(15, 50, "101.5", "89", "400", "100"),
                parked(20, 0, "102.0", "88"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertEquals(1, trips.size());
        BigDecimal consumed = trips.get(0).consumedKwh();
        assertNotNull(consumed);
        // 3 intervals of 5s with 40 kW: 3 * 5/3600 * 40 = 0.1667 kWh (approx)
        // Trapezoidal might give slightly different value, but should be > 0.1 and < 0.3
        assertTrue(consumed.compareTo(new BigDecimal("0.10")) > 0,
                "consumed kWh should be positive and meaningful, got " + consumed);
        assertTrue(consumed.compareTo(new BigDecimal("0.30")) < 0,
                "consumed kWh upper bound check, got " + consumed);
    }

    @Test
    void tripContinuesThroughBriefStopAtTrafficLight() {
        XpengTripDetector detector = new XpengTripDetector();
        // Driver is in D, stops at traffic light briefly (~30s), continues
        List<XpengTelematicsRow> rows = sequence(
                parked(0, 0, "100.0", "80"),
                driving(5, 50, "100.5", "80"),
                driving(10, 0, "100.5", "80"),  // stopped at light, still in D
                driving(15, 0, "100.5", "80"),
                driving(20, 0, "100.5", "80"),
                driving(25, 0, "100.5", "80"),
                driving(30, 0, "100.5", "80"),
                driving(35, 50, "100.7", "80"),
                parked(40, 0, "101.0", "79"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertEquals(1, trips.size(),
                "brief stops while still in gear D should not split a trip");
    }

    @Test
    void gapRowWithoutGearDoesNotSplitTrip() {
        // Beim Merge der 3 CSV-Cluster ueber timer kann ein Zeitpunkt nur im
        // power-Stream liegen (kein operation-Sample) -> gear UND speed sind dann
        // null. So ein Datenluecken-Sample darf die laufende Fahrt NICHT beenden,
        // sonst zerfaellt eine Fahrt in mehrere Fragmente.
        XpengTripDetector detector = new XpengTripDetector();
        List<XpengTelematicsRow> rows = sequence(
                parked(0, 0, "100.0", "80"),
                driving(5, 50, "100.5", "80"),
                gapNoGearNoSpeed(10, "101.0", "79"), // power-only Merge-Epoch mitten in der Fahrt
                driving(15, 55, "101.5", "79"),
                parked(20, 0, "102.0", "78"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertEquals(1, trips.size(), "ein gear-loses Luecken-Sample darf die Fahrt nicht splitten");
        assertEquals(0, new BigDecimal("2.0").compareTo(trips.get(0).distanceKm()));
    }

    @Test
    void startsTripFromSpeedWhenGearMissing() {
        // Fahrtbeginn, aber das erste Bewegungs-Sample hat kein gear (Modul schlief),
        // nur speed>0. Die Fahrt muss trotzdem erkannt werden.
        XpengTripDetector detector = new XpengTripDetector();
        List<XpengTelematicsRow> rows = sequence(
                parked(0, 0, "100.0", "80"),
                movingNoGear(5, 40, "100.4", "80"),
                driving(10, 55, "101.0", "79"),
                parked(15, 0, "101.2", "79"));
        List<DetectedTrip> trips = detect(detector, rows);
        assertEquals(1, trips.size(), "speed>0 ohne gear muss eine Fahrt starten");
    }

    private XpengTelematicsRow gapNoGearNoSpeed(int sec, String odo, String soc) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                null, null,
                new BigDecimal(odo), new BigDecimal(soc),
                null, null, null, null, null, java.util.Map.of());
    }

    private XpengTelematicsRow movingNoGear(int sec, int speed, String odo, String soc) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                BigDecimal.valueOf(speed), null,
                new BigDecimal(odo), new BigDecimal(soc),
                null, null, null, null, null, java.util.Map.of());
    }

    // -- helpers --

    private List<DetectedTrip> detect(XpengTripDetector detector, List<XpengTelematicsRow> rows) {
        List<DetectedTrip> emitted = new ArrayList<>();
        for (XpengTelematicsRow row : rows) {
            detector.consume(row).ifPresent(emitted::add);
        }
        detector.finish().ifPresent(emitted::add);
        return emitted;
    }

    @Test
    void aggregatesDrivingStyleExtras() {
        XpengTripDetector detector = new XpengTripDetector();
        // Fahrt mit einem harten Beschleunigungs-Event (0.5g) und einem harten
        // Bremsereignis (-0.4g) sowie scharfer Kurve (|lat|=0.45g).
        List<XpengTelematicsRow> rows = new ArrayList<>();
        rows.add(drivingWithExtras(0, 50, "100.0", "90", "0.10", "0.05", "20"));
        rows.add(drivingWithExtras(5, 60, "100.5", "89", "0.50", "0.10", "80"));
        rows.add(drivingWithExtras(10, 60, "101.0", "89", "0.10", "0.45", "30"));
        rows.add(drivingWithExtras(15, 40, "101.5", "88", "-0.40", "-0.10", "0"));
        rows.add(parked(20, 0, "102.0", "88"));

        List<DetectedTrip> trips = detect(detector, rows);
        assertEquals(1, trips.size());
        java.util.Map<String, Object> extras = trips.get(0).telemetryExtras();
        assertNotNull(extras);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> driving = (java.util.Map<String, Object>) extras.get("driving_style");
        assertNotNull(driving);
        assertEquals(new BigDecimal("0.50"), driving.get("max_long_accel_g"));
        assertEquals(new BigDecimal("-0.40"), driving.get("max_long_deccel_g"));
        assertEquals(new BigDecimal("0.45"), driving.get("max_lat_accel_g"));
        assertEquals(1, driving.get("harsh_accel_count"));
        assertEquals(1, driving.get("harsh_brake_count"));
        assertEquals(1, driving.get("harsh_corner_count"));
        assertEquals(new BigDecimal("80.0"), driving.get("accel_pedal_max_pct"));
    }

    private XpengTelematicsRow drivingWithExtras(int sec, int speed, String odo, String soc,
                                                  String longAccel, String latAccel, String pedal) {
        java.util.Map<String, BigDecimal> extras = new java.util.HashMap<>();
        extras.put(XpengExtraKeys.LONG_ACCEL_G, new BigDecimal(longAccel));
        extras.put(XpengExtraKeys.LAT_ACCEL_G,  new BigDecimal(latAccel));
        extras.put(XpengExtraKeys.ACCEL_PEDAL_PCT, new BigDecimal(pedal));
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                BigDecimal.valueOf(speed), 1,
                new BigDecimal(odo), new BigDecimal(soc),
                null, null, null, null, null, extras);
    }

    private List<XpengTelematicsRow> sequence(XpengTelematicsRow... rows) {
        return List.of(rows);
    }

    private XpengTelematicsRow parked(int sec, int speed, String odo, String soc) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                BigDecimal.valueOf(speed), 4,
                new BigDecimal(odo), new BigDecimal(soc),
                null, null, null, null, null, java.util.Map.of());
    }

    private XpengTelematicsRow driving(int sec, int speed, String odo, String soc) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                BigDecimal.valueOf(speed), 1,
                new BigDecimal(odo), new BigDecimal(soc),
                null, null, null, null, null, java.util.Map.of());
    }

    private XpengTelematicsRow drivingWithPower(int sec, int speed, String odo, String soc,
                                                 String volt, String current) {
        return new XpengTelematicsRow(
                T0.plusSeconds(sec),
                BigDecimal.valueOf(speed), 1,
                new BigDecimal(odo), new BigDecimal(soc),
                new BigDecimal(volt), new BigDecimal(current),
                null, null, null, java.util.Map.of());
    }
}
