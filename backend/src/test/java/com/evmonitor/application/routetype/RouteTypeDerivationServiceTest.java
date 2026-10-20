package com.evmonitor.application.routetype;

import com.evmonitor.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteTypeDerivationServiceTest {

    @Mock EvLogRepository evLogRepository;
    @Mock EvTripRepository evTripRepository;
    @Mock CarRepository carRepository;

    RouteTypeDerivationService service;

    final UUID carId = UUID.randomUUID();
    final LocalDateTime prevAt = LocalDateTime.of(2026, 3, 1, 8, 0);
    final LocalDateTime currAt = LocalDateTime.of(2026, 3, 1, 20, 0);
    Car car;

    @BeforeEach
    void setUp() {
        service = new RouteTypeDerivationService(evLogRepository, evTripRepository, carRepository);
        car = Car.builder().id(carId).customNetCapacityKwh(new BigDecimal("70")).build();
    }

    private EvLog log(LocalDateTime at, Integer odo, RouteType type, RouteTypeSource source) {
        return EvLog.builder().id(UUID.randomUUID()).carId(carId).kwhCharged(BigDecimal.TEN)
                .loggedAt(at).chargeDurationMinutes(60).odometerKm(odo).routeType(type).routeTypeSource(source).build();
    }

    private EvTrip trip(String source, double km, int startHour, int minutes, String type) {
        OffsetDateTime s = OffsetDateTime.of(2026, 3, 1, startHour, 0, 0, 0, ZoneOffset.UTC);
        return EvTrip.builder().id(UUID.randomUUID()).carId(carId).dataSource(source).distanceKm(BigDecimal.valueOf(km))
                .tripStartedAt(s).tripEndedAt(s.plusMinutes(minutes)).routeType(type).build();
    }

    private void stub(EvLog current, EvLog previous, List<EvTrip> trips) {
        when(evLogRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(evLogRepository.findPreviousByCarId(carId, current.getLoggedAt())).thenReturn(Optional.ofNullable(previous));
        if (previous != null) {
            lenient().when(carRepository.findById(carId)).thenReturn(Optional.of(car));
            lenient().when(evTripRepository.findByCarIdAndTripStartedAtBetweenOrderByTripStartedAtAsc(eq(carId), any(), any()))
                    .thenReturn(trips);
        }
    }

    @Test
    void wallboxLogGetsHighwayFromTeslaLiveTrips() {
        EvLog prev = log(prevAt, 1000, null, null);
        EvLog curr = log(currAt, 1200, null, null);
        stub(curr, prev, List.of(trip("TESLA_LIVE", 180, 10, 110, null)));

        Optional<RouteTypeDerivationService.Derivation> d = service.derive(curr.getId());

        assertEquals(RouteType.HIGHWAY, d.orElseThrow().routeType());
        assertEquals(RouteTypeSource.TRIPS, d.get().source());
        verify(evLogRepository).updateRouteType(curr.getId(), RouteType.HIGHWAY, RouteTypeSource.TRIPS);
    }

    @Test
    void windowStartsAtEndOfPreviousCharge() {
        EvLog prev = log(prevAt, 1000, null, null);
        EvLog curr = log(currAt, 1200, null, null);
        stub(curr, prev, List.of());

        service.derive(curr.getId());

        verify(evTripRepository).findByCarIdAndTripStartedAtBetweenOrderByTripStartedAtAsc(
                carId, prevAt.plusMinutes(60).atOffset(ZoneOffset.UTC), currAt.atOffset(ZoneOffset.UTC));
    }

    @Test
    void smartcarTripsAreIgnored() {
        EvLog prev = log(prevAt, 1000, null, null);
        EvLog curr = log(currAt, 1200, null, null);
        stub(curr, prev, List.of(trip("SMARTCAR_LIVE", 180, 10, 110, "HIGHWAY")));

        assertTrue(service.derive(curr.getId()).isEmpty());
        verify(evLogRepository, never()).updateRouteType(any(), any(), any());
    }

    @Test
    void tripsEndingAfterChargeStartAreIgnored() {
        EvLog prev = log(prevAt, 1000, null, null);
        EvLog curr = log(currAt, 1200, null, null);
        stub(curr, prev, List.of(trip("TESLA_LIVE", 180, 19, 120, "HIGHWAY")));

        assertTrue(service.derive(curr.getId()).isEmpty());
    }

    @Test
    void userValueIsNeverOverridden() {
        EvLog prev = log(prevAt, 1000, null, null);
        EvLog curr = log(currAt, 1200, RouteType.CITY, RouteTypeSource.USER);
        stub(curr, prev, List.of(trip("TESLA_LIVE", 180, 10, 110, "HIGHWAY")));

        assertTrue(service.derive(curr.getId()).isEmpty());
        verify(evLogRepository, never()).updateRouteType(any(), any(), any());
    }

    @Test
    void legacyCombinedWithoutSourceIsOverriddenByTrips() {
        EvLog prev = log(prevAt, 1000, null, null);
        EvLog curr = log(currAt, 1200, RouteType.COMBINED, null);
        stub(curr, prev, List.of(trip("TESLA_LIVE", 180, 10, 110, "HIGHWAY")));

        assertEquals(RouteType.HIGHWAY, service.derive(curr.getId()).orElseThrow().routeType());
    }

    @Test
    void noPreviousCharge_isEmpty() {
        EvLog curr = log(currAt, 1200, null, null);
        stub(curr, null, List.of());

        assertTrue(service.derive(curr.getId()).isEmpty());
        verifyNoInteractions(evTripRepository);
    }

    @Test
    void unknownLog_isEmpty() {
        UUID id = UUID.randomUUID();
        when(evLogRepository.findById(id)).thenReturn(Optional.empty());
        assertTrue(service.derive(id).isEmpty());
    }
}
