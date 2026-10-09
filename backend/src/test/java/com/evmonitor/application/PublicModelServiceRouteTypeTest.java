package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class PublicModelServiceRouteTypeTest extends AbstractIntegrationTest {

    @Autowired
    private PublicModelService publicModelService;

    @Autowired
    private CacheManager cacheManager;

    private static final CarBrand.CarModel MODEL = CarBrand.CarModel.FIAT_500E;

    @BeforeEach
    void clearCache() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void routeTypeDistribution_reflectsClassifiedAndUnknownCounts() {
        User user = createAndSaveUser("routetype1-" + System.currentTimeMillis() + "@example.com");
        Car car = createAndSaveCar(user.getId(), MODEL);

        saveLogWithRouteType(car.getId(), RouteType.HIGHWAY);
        saveLogWithRouteType(car.getId(), RouteType.HIGHWAY);
        saveLogWithRouteType(car.getId(), RouteType.HIGHWAY);
        saveLogWithRouteType(car.getId(), RouteType.CITY);
        saveLogWithRouteType(car.getId(), RouteType.CITY);
        saveLogWithRouteType(car.getId(), null);

        Optional<PublicModelStatsResponse> result = publicModelService.getModelStats(
                MODEL.getBrand().getDisplayString(), MODEL.getDisplayName(), false);

        assertTrue(result.isPresent());
        var dist = result.get().routeTypeDistribution();
        assertNotNull(dist);

        int highway = dist.stream().filter(e -> "HIGHWAY".equals(e.routeType())).mapToInt(PublicModelStatsResponse.RouteTypeEntry::count).sum();
        int city    = dist.stream().filter(e -> "CITY".equals(e.routeType())).mapToInt(PublicModelStatsResponse.RouteTypeEntry::count).sum();
        int unknown = dist.stream().filter(e -> "UNKNOWN".equals(e.routeType())).mapToInt(PublicModelStatsResponse.RouteTypeEntry::count).sum();

        assertEquals(3, highway);
        assertEquals(2, city);
        assertEquals(1, unknown);
    }

    @Test
    void routeTypeDistribution_allNullRouteType_returnsOnlyUnknown() {
        User user = createAndSaveUser("routetype2-" + System.currentTimeMillis() + "@example.com");
        Car car = createAndSaveCar(user.getId(), MODEL);

        saveLogWithRouteType(car.getId(), null);
        saveLogWithRouteType(car.getId(), null);

        Optional<PublicModelStatsResponse> result = publicModelService.getModelStats(
                MODEL.getBrand().getDisplayString(), MODEL.getDisplayName(), false);

        assertTrue(result.isPresent());
        var dist = result.get().routeTypeDistribution();
        assertNotNull(dist);

        long classifiedCount = dist.stream()
                .filter(e -> !"UNKNOWN".equals(e.routeType()))
                .mapToInt(PublicModelStatsResponse.RouteTypeEntry::count)
                .sum();
        assertEquals(0, classifiedCount);
    }

    @Test
    void routeTypeConsumption_needsTenTripsPerType() {
        User user = createAndSaveUser("routetype3-" + System.currentTimeMillis() + "@example.com");
        Car car = carRepository.save(Car.createNew(user.getId(), MODEL, 2023,
                "RT-" + System.nanoTime() % 100000, null, new BigDecimal("42.0"), new BigDecimal("320"), null));

        // 11 SoC logs → 10 CITY trips of 100 km; then 5 HIGHWAY trips (below the threshold)
        LocalDateTime t = LocalDateTime.of(2025, 6, 1, 8, 0);
        int odo = 20000;
        saveSocLog(car.getId(), odo, 80, t, null);
        for (int i = 1; i <= 10; i++) saveSocLog(car.getId(), odo += 100, 80, t.plusDays(i), RouteType.CITY);
        for (int i = 11; i <= 15; i++) saveSocLog(car.getId(), odo += 100, 80, t.plusDays(i), RouteType.HIGHWAY);

        var rtc = publicModelService.getModelStats(
                MODEL.getBrand().getDisplayString(), MODEL.getDisplayName(), false).orElseThrow().routeTypeConsumption();

        assertNotNull(rtc);
        assertNotNull(rtc.cityKwhPer100km());
        assertEquals(10, rtc.cityLogCount());
        assertNull(rtc.highwayKwhPer100km(), "5 trips are below the 10-trip threshold");
        assertEquals(5, rtc.highwayLogCount());
        assertNull(rtc.combinedKwhPer100km());
    }

    @Test
    void routeTypeConsumption_isNullWithoutAnyClassifiedTrips() {
        User user = createAndSaveUser("routetype4-" + System.currentTimeMillis() + "@example.com");
        Car car = createAndSaveCar(user.getId(), MODEL);
        saveLogWithRouteType(car.getId(), null);

        var stats = publicModelService.getModelStats(
                MODEL.getBrand().getDisplayString(), MODEL.getDisplayName(), false).orElseThrow();

        assertNull(stats.routeTypeConsumption());
    }

    /** 15 kWh charged to 80 % on a 42 kWh car: about 15 kWh/100 km per 100 km trip. */
    private void saveSocLog(java.util.UUID carId, int odometerKm, int socAfter, LocalDateTime at, RouteType routeType) {
        evLogRepository.save(EvLog.createNew(
                carId, new BigDecimal("15.0"), new BigDecimal("5.00"), 60,
                "u6g2d1", odometerKm, new BigDecimal("11.0"), new BigDecimal(socAfter), at,
                ChargingType.UNKNOWN, routeType, null, false, null));
    }

    private void saveLogWithRouteType(java.util.UUID carId, RouteType routeType) {
        evLogRepository.save(EvLog.createNew(
                carId,
                new BigDecimal("18.0"),
                new BigDecimal("10.00"),
                60,
                "u6g2d1",
                50000,
                new BigDecimal("11.0"),
                null,
                LocalDateTime.now(),
                ChargingType.UNKNOWN,
                routeType, null,
                false, null
        ));
    }
}
