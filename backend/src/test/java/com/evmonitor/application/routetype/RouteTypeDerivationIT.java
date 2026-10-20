package com.evmonitor.application.routetype;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Ableitung pro Auto, nicht pro Quelle: eine Wallbox-Ladung bekommt den Typ aus Tesla-Live-Fahrten desselben Autos. */
class RouteTypeDerivationIT extends AbstractIntegrationTest {

    @Autowired private RouteTypeDerivationService service;
    @Autowired private EvLogRepository evLogRepository;
    @Autowired private EvTripRepository evTripRepository;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("routetype-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        userId = user.getId();
        carId = carRepository.save(Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023,
                "RT-" + UUID.randomUUID().toString().substring(0, 8), "Long Range",
                new BigDecimal("75.0"), new BigDecimal("275.0"), null)).getId();
    }

    private EvLog wallboxLog(LocalDateTime at, int odometerKm) {
        return evLogRepository.save(EvLog.createNewWithSource(carId, new BigDecimal("30.0"), null, 60, "u33d1", odometerKm,
                new BigDecimal("11.0"), new BigDecimal("80"), at, DataSource.WALLBOX_GOE, ChargingType.AC, null));
    }

    private void teslaLiveTrip(OffsetDateTime start, int minutes, double km) {
        evTripRepository.save(EvTrip.builder().userId(userId).carId(carId).dataSource(EvTrip.DATA_SOURCE_TESLA_LIVE)
                .tripStartedAt(start).tripEndedAt(start.plusMinutes(minutes)).distanceKm(BigDecimal.valueOf(km))
                .status("COMPLETED").createdAt(OffsetDateTime.now()).build());
    }

    @Test
    void wallboxChargeGetsHighwayFromTeslaLiveTrips() {
        LocalDateTime prevAt = LocalDateTime.of(2026, 3, 1, 7, 0);
        LocalDateTime currAt = LocalDateTime.of(2026, 3, 1, 20, 0);
        wallboxLog(prevAt, 10_000);
        EvLog current = wallboxLog(currAt, 10_200);
        // 2 Fahrten ohne Typ: 150 km in 90 min (100 km/h) und 40 km in 60 min (40 km/h); 190 von 200 km abgedeckt
        teslaLiveTrip(OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC), 90, 150);
        teslaLiveTrip(OffsetDateTime.of(2026, 3, 1, 14, 0, 0, 0, ZoneOffset.UTC), 60, 40);

        var derivation = service.derive(current.getId());

        assertEquals(RouteType.HIGHWAY, derivation.orElseThrow().routeType());
        EvLog reloaded = evLogRepository.findById(current.getId()).orElseThrow();
        assertEquals(RouteType.HIGHWAY, reloaded.getRouteType());
        assertEquals(RouteTypeSource.TRIPS, reloaded.getRouteTypeSource());
    }

    @Test
    void tooLittleTripCoverage_leavesRouteTypeEmpty() {
        wallboxLog(LocalDateTime.of(2026, 3, 1, 7, 0), 10_000);
        EvLog current = wallboxLog(LocalDateTime.of(2026, 3, 1, 20, 0), 10_200);
        teslaLiveTrip(OffsetDateTime.of(2026, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC), 60, 80);

        assertTrue(service.derive(current.getId()).isEmpty());
        assertNull(evLogRepository.findById(current.getId()).orElseThrow().getRouteType());
    }
}
