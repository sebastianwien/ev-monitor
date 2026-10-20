package com.evmonitor.application.routetype;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.RouteType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RouteTypeRulesTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 3, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    private static EvTrip trip(double km, int minutes, String routeType) {
        return EvTrip.builder()
                .id(UUID.randomUUID())
                .distanceKm(BigDecimal.valueOf(km))
                .tripStartedAt(T0)
                .tripEndedAt(T0.plusMinutes(minutes))
                .routeType(routeType)
                .build();
    }

    private static EvLog log(Integer odometerKm) {
        return EvLog.builder().carId(UUID.randomUUID()).kwhCharged(BigDecimal.TEN)
                .loggedAt(LocalDateTime.of(2026, 3, 1, 20, 0)).odometerKm(odometerKm).build();
    }

    // ---- classifyTrip v2 (20.10.2026): Schnitt ab 90 Autobahn, Spitze ab 140 bei Schnitt ab 70 auch; Stadt unter 50 und Spitze unter 80 ----

    @Test
    void classifyTrip_byAvgSpeedOnly() {
        assertEquals(RouteType.HIGHWAY, RouteTypeRules.classifyTrip(new BigDecimal("90"), null));
        assertEquals(RouteType.COMBINED, RouteTypeRules.classifyTrip(new BigDecimal("89.9"), null), "Landstraße oder 80er-Schnitt ist gemischt");
        assertEquals(RouteType.COMBINED, RouteTypeRules.classifyTrip(new BigDecimal("50"), null));
        assertEquals(RouteType.CITY, RouteTypeRules.classifyTrip(new BigDecimal("49.9"), null));
        assertNull(RouteTypeRules.classifyTrip(null, new BigDecimal("150")));
    }

    @Test
    void classifyTrip_maxSpeedRefines() {
        assertEquals(RouteType.HIGHWAY, RouteTypeRules.classifyTrip(new BigDecimal("70"), new BigDecimal("140")), "Autobahn mit Stau");
        assertEquals(RouteType.COMBINED, RouteTypeRules.classifyTrip(new BigDecimal("69.9"), new BigDecimal("180")), "zu langsam im Schnitt");
        assertEquals(RouteType.COMBINED, RouteTypeRules.classifyTrip(new BigDecimal("75"), new BigDecimal("139.9")));
        assertEquals(RouteType.COMBINED, RouteTypeRules.classifyTrip(new BigDecimal("40"), new BigDecimal("80")), "langsam, aber Landstraßen-Spitze");
        assertEquals(RouteType.CITY, RouteTypeRules.classifyTrip(new BigDecimal("40"), new BigDecimal("79.9")));
    }

    @Test
    void classifyTrip_fromDistanceAndDuration() {
        assertEquals(RouteType.HIGHWAY, RouteTypeRules.classifyTrip(new BigDecimal("90"), T0, T0.plusHours(1), null));
        assertEquals(RouteType.HIGHWAY, RouteTypeRules.classifyTrip(new BigDecimal("70"), T0, T0.plusHours(1), new BigDecimal("150")));
        assertEquals(RouteType.CITY, RouteTypeRules.classifyTrip(new BigDecimal("40"), T0, T0.plusHours(1), null));
    }

    @Test
    void classifyTrip_noDurationOrTinyTrip_isNull() {
        assertNull(RouteTypeRules.classifyTrip(new BigDecimal("10"), T0, T0, null));
        assertNull(RouteTypeRules.classifyTrip(new BigDecimal("10"), T0, null, null));
        assertNull(RouteTypeRules.classifyTrip(new BigDecimal("0.4"), T0, T0.plusMinutes(1), null));
        assertNull(RouteTypeRules.classifyTrip(null, T0, T0.plusMinutes(10), null));
    }

    // ---- byKmShare: ein Typ braucht 75 % der klassifizierten km, sonst COMBINED ----

    @Test
    void byKmShare_speedRuleBeatsStoredType() {
        // 100 km gespeichert HIGHWAY, aber 100 km in 90 min = 67 km/h ohne Spitze -> COMBINED nach Regel
        assertEquals(Optional.of(RouteType.COMBINED), RouteTypeRules.byKmShare(List.of(trip(100, 90, "HIGHWAY"))));
    }

    @Test
    void byKmShare_storedTypeIsFallbackWithoutSpeed() {
        EvTrip noDuration = EvTrip.builder().id(UUID.randomUUID()).distanceKm(new BigDecimal("50")).routeType("CITY").build();
        assertEquals(Optional.of(RouteType.CITY), RouteTypeRules.byKmShare(List.of(noDuration)));
    }

    @Test
    void byKmShare_needsThreeQuarters() {
        // 75 von 100 km Autobahn -> HIGHWAY; 74 -> COMBINED
        assertEquals(Optional.of(RouteType.HIGHWAY), RouteTypeRules.byKmShare(List.of(trip(75, 45, null), trip(25, 60, null))));
        assertEquals(Optional.of(RouteType.COMBINED), RouteTypeRules.byKmShare(List.of(trip(74, 45, null), trip(26, 60, null))));
        assertEquals(Optional.of(RouteType.CITY), RouteTypeRules.byKmShare(List.of(trip(30, 60, null), trip(10, 6, null))));
    }

    @Test
    void byKmShare_noClassifiableKm_isEmpty() {
        assertTrue(RouteTypeRules.byKmShare(List.of()).isEmpty());
        assertTrue(RouteTypeRules.byKmShare(List.of(trip(0.3, 1, null))).isEmpty());
    }

    // ---- fromTrips: Mindestabdeckung 50 % der Odometer-Differenz, beide Odometer nötig ----

    @Test
    void fromTrips_coverageBelowHalf_isEmpty() {
        List<EvTrip> trips = List.of(trip(49, 30, null));
        assertTrue(RouteTypeRules.fromTrips(log(1000), log(1100), trips).isEmpty());
        assertEquals(Optional.of(RouteType.HIGHWAY), RouteTypeRules.fromTrips(log(1000), log(1098), trips));
    }

    @Test
    void fromTrips_missingOdometer_isEmpty() {
        List<EvTrip> trips = List.of(trip(90, 60, null));
        assertTrue(RouteTypeRules.fromTrips(log(null), log(1100), trips).isEmpty());
        assertTrue(RouteTypeRules.fromTrips(log(1000), log(null), trips).isEmpty());
        assertTrue(RouteTypeRules.fromTrips(log(1100), log(1000), trips).isEmpty(), "Odometer rückwärts");
    }

    // ---- Faustregel: mittlere Leistung aus SoC-Hub, Lücke 0,5 bis 12 h, ab 25 kW Autobahn ----

    private static Car car70kwh(String degradationPercent) {
        return Car.builder().id(UUID.randomUUID()).customNetCapacityKwh(new BigDecimal("70"))
                .batteryDegradationPercent(degradationPercent != null ? new BigDecimal(degradationPercent) : null).build();
    }

    private static EvLog charge(LocalDateTime at, Integer durationMin, String socBefore, String socAfter) {
        return EvLog.builder().carId(UUID.randomUUID()).kwhCharged(BigDecimal.TEN).loggedAt(at)
                .chargeDurationMinutes(durationMin)
                .socBeforeChargePercent(socBefore != null ? new BigDecimal(socBefore) : null)
                .socAfterChargePercent(socAfter != null ? new BigDecimal(socAfter) : null).build();
    }

    private static final LocalDateTime P = LocalDateTime.of(2026, 3, 1, 8, 0);

    @Test
    void meanPower_usesSocHubAndGapAfterPreviousChargeEnd() {
        // Vorladung endet 09:00 (Start 08:00 + 60 min), aktuelle startet 11:00: 2 h. Hub 90 -> 10 = 80 % von 70 kWh = 56 kWh -> 28 kW
        EvLog prev = charge(P, 60, "20", "90");
        EvLog curr = charge(P.plusHours(3), null, "10", "80");
        assertEquals(new BigDecimal("28.00"), RouteTypeRules.meanPowerKw(prev, curr, car70kwh(null)).orElseThrow());
        assertEquals(Optional.of(RouteType.HIGHWAY), RouteTypeRules.fromMeanPower(prev, curr, car70kwh(null)));
    }

    @Test
    void meanPower_usesSohAdjustedCapacity() {
        // 10 % Degradation: 63 kWh, Hub 80 % = 50,4 kWh in 2 h = 25,2 kW
        EvLog prev = charge(P, 60, "20", "90");
        EvLog curr = charge(P.plusHours(3), null, "10", "80");
        assertEquals(new BigDecimal("25.20"), RouteTypeRules.meanPowerKw(prev, curr, car70kwh("10")).orElseThrow());
    }

    @Test
    void fromMeanPower_thresholdIs25kw() {
        // 70 kWh, Hub 50 % = 35 kWh: in 1,4 h = 25,0 kW -> Autobahn; in 1,5 h = 23,3 kW -> nichts
        EvLog prev = charge(P, 0, "20", "90");
        assertEquals(Optional.of(RouteType.HIGHWAY),
                RouteTypeRules.fromMeanPower(prev, charge(P.plusMinutes(84), null, "40", null), car70kwh(null)));
        assertTrue(RouteTypeRules.fromMeanPower(prev, charge(P.plusMinutes(90), null, "40", null), car70kwh(null)).isEmpty());
    }

    @Test
    void fromMeanPower_gapOutsideHalfHourToTwelveHours_isEmpty() {
        EvLog prev = charge(P, 0, "20", "100");
        assertTrue(RouteTypeRules.meanPowerKw(prev, charge(P.plusMinutes(29), null, "0", null), car70kwh(null)).isEmpty());
        assertTrue(RouteTypeRules.meanPowerKw(prev, charge(P.plusMinutes(30), null, "0", null), car70kwh(null)).isPresent());
        assertTrue(RouteTypeRules.meanPowerKw(prev, charge(P.plusHours(12), null, "0", null), car70kwh(null)).isPresent());
        assertTrue(RouteTypeRules.meanPowerKw(prev, charge(P.plusHours(12).plusMinutes(1), null, "0", null), car70kwh(null)).isEmpty());
    }

    @Test
    void meanPower_missingSocOrCapacityOrNegativeHub_isEmpty() {
        EvLog prev = charge(P, 0, "20", "90");
        EvLog curr = charge(P.plusHours(2), null, "10", null);
        assertTrue(RouteTypeRules.meanPowerKw(charge(P, 0, "20", null), curr, car70kwh(null)).isEmpty());
        assertTrue(RouteTypeRules.meanPowerKw(prev, charge(P.plusHours(2), null, null, null), car70kwh(null)).isEmpty());
        assertTrue(RouteTypeRules.meanPowerKw(prev, curr, Car.builder().id(UUID.randomUUID()).build()).isEmpty());
        assertTrue(RouteTypeRules.meanPowerKw(prev, charge(P.plusHours(2), null, "95", null), car70kwh(null)).isEmpty(), "SoC gestiegen");
    }
}
