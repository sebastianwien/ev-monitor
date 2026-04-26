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
