package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Rangfolge der Streckentyp-Herkunft: USER > SOURCE > TRIPS > HEURISTIC > NULL. */
class EvLogRouteTypeSourceTest {

    private static EvLog log(RouteType type, RouteTypeSource source) {
        return EvLog.builder()
                .carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("10"))
                .loggedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .routeType(type)
                .routeTypeSource(source)
                .build();
    }

    @Test
    void withoutRouteType_anySourceMayWrite() {
        assertTrue(log(null, null).mayOverrideRouteType(RouteTypeSource.HEURISTIC));
    }

    @Test
    void routeTypeWithoutSource_isLegacy_anySourceMayWrite() {
        assertTrue(log(RouteType.COMBINED, null).mayOverrideRouteType(RouteTypeSource.TRIPS));
        assertTrue(log(RouteType.COMBINED, null).mayOverrideRouteType(RouteTypeSource.HEURISTIC));
    }

    @Test
    void lowerRankNeverOverridesHigher() {
        assertFalse(log(RouteType.HIGHWAY, RouteTypeSource.USER).mayOverrideRouteType(RouteTypeSource.SOURCE));
        assertFalse(log(RouteType.HIGHWAY, RouteTypeSource.SOURCE).mayOverrideRouteType(RouteTypeSource.TRIPS));
        assertFalse(log(RouteType.HIGHWAY, RouteTypeSource.TRIPS).mayOverrideRouteType(RouteTypeSource.HEURISTIC));
    }

    @Test
    void sameRankMayRecompute() {
        assertTrue(log(RouteType.HIGHWAY, RouteTypeSource.TRIPS).mayOverrideRouteType(RouteTypeSource.TRIPS));
    }

    @Test
    void higherRankOverridesLower() {
        assertTrue(log(RouteType.HIGHWAY, RouteTypeSource.HEURISTIC).mayOverrideRouteType(RouteTypeSource.TRIPS));
        assertTrue(log(RouteType.HIGHWAY, RouteTypeSource.TRIPS).mayOverrideRouteType(RouteTypeSource.USER));
    }

    @Test
    void sourceIsDroppedWhenRouteTypeIsNull() {
        assertNull(log(null, RouteTypeSource.USER).getRouteTypeSource());
    }

    @Test
    void heuristicDoesNotCountInStatistics() {
        assertFalse(RouteTypeSource.HEURISTIC.countsInStatistics());
        assertTrue(RouteTypeSource.USER.countsInStatistics());
        assertTrue(RouteTypeSource.SOURCE.countsInStatistics());
        assertTrue(RouteTypeSource.TRIPS.countsInStatistics());
    }
}
