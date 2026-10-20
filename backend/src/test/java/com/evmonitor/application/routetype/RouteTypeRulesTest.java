package com.evmonitor.application.routetype;

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

    // ---- classifyTrip: Tempo = km / h, >= 80 Autobahn, < 50 Stadt ----

    @Test
    void classifyTrip_thresholds() {
        assertEquals(RouteType.HIGHWAY, RouteTypeRules.classifyTrip(new BigDecimal("80"), T0, T0.plusHours(1)));
        assertEquals(RouteType.COMBINED, RouteTypeRules.classifyTrip(new BigDecimal("79.9"), T0, T0.plusHours(1)));
        assertEquals(RouteType.COMBINED, RouteTypeRules.classifyTrip(new BigDecimal("50"), T0, T0.plusHours(1)));
        assertEquals(RouteType.CITY, RouteTypeRules.classifyTrip(new BigDecimal("49.9"), T0, T0.plusHours(1)));
    }

    @Test
    void classifyTrip_noDurationOrTinyTrip_isNull() {
        assertNull(RouteTypeRules.classifyTrip(new BigDecimal("10"), T0, T0));
        assertNull(RouteTypeRules.classifyTrip(new BigDecimal("10"), T0, null));
        assertNull(RouteTypeRules.classifyTrip(new BigDecimal("0.4"), T0, T0.plusMinutes(1)));
        assertNull(RouteTypeRules.classifyTrip(null, T0, T0.plusMinutes(10)));
    }

    // ---- dominantByKm: strikte Mehrheit, sonst COMBINED ----

    @Test
    void dominantByKm_usesStoredTypeFirst_thenSpeed() {
        // 100 km gespeichert HIGHWAY, 30 km ohne Typ aber 30 km in 30 min = 60 km/h -> COMBINED
        Optional<RouteType> r = RouteTypeRules.dominantByKm(List.of(trip(100, 60, "HIGHWAY"), trip(30, 30, null)));
        assertEquals(Optional.of(RouteType.HIGHWAY), r);
    }

    @Test
    void dominantByKm_tie_isCombined() {
        assertEquals(Optional.of(RouteType.COMBINED),
                RouteTypeRules.dominantByKm(List.of(trip(50, 30, "HIGHWAY"), trip(50, 120, "CITY"))));
    }

    @Test
    void dominantByKm_noClassifiableKm_isEmpty() {
        assertTrue(RouteTypeRules.dominantByKm(List.of()).isEmpty());
        assertTrue(RouteTypeRules.dominantByKm(List.of(trip(0.3, 1, null))).isEmpty());
    }

    // ---- fromTrips: Mindestabdeckung 50 % der Odometer-Differenz, beide Odometer nötig ----

    @Test
    void fromTrips_coverageBelowHalf_isEmpty() {
        List<EvTrip> trips = List.of(trip(49, 30, "HIGHWAY"));
        assertTrue(RouteTypeRules.fromTrips(log(1000), log(1100), trips).isEmpty());
        assertEquals(Optional.of(RouteType.HIGHWAY), RouteTypeRules.fromTrips(log(1000), log(1098), trips));
    }

    @Test
    void fromTrips_missingOdometer_isEmpty() {
        List<EvTrip> trips = List.of(trip(90, 60, "HIGHWAY"));
        assertTrue(RouteTypeRules.fromTrips(log(null), log(1100), trips).isEmpty());
        assertTrue(RouteTypeRules.fromTrips(log(1000), log(null), trips).isEmpty());
        assertTrue(RouteTypeRules.fromTrips(log(1100), log(1000), trips).isEmpty(), "Odometer rückwärts");
    }
}
