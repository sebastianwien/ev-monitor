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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that PublicModelStatsResponse.uniqueCars reflects the number of distinct
 * cars (not users) contributing to a model's stats.
 *
 * Uses CUPRA_BORN to avoid data collisions with other test classes.
 */
@Transactional
class PublicModelServiceUniqueCarCountTest extends AbstractIntegrationTest {

    @Autowired
    private PublicModelService publicModelService;

    @Autowired
    private CacheManager cacheManager;

    private static final CarBrand.CarModel MODEL = CarBrand.CarModel.CUPRA_BORN;

    @BeforeEach
    void clearCache() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void getModelStats_twoCarsFromDifferentUsers_uniqueCarsIsTwo() {
        User user1 = createAndSaveUser("uniquecars1-" + System.currentTimeMillis() + "@example.com");
        User user2 = createAndSaveUser("uniquecars2-" + System.currentTimeMillis() + "@example.com");

        Car car1 = createAndSaveCar(user1.getId(), MODEL);
        Car car2 = createAndSaveCar(user2.getId(), MODEL);

        saveLog(car1.getId());
        saveLog(car2.getId());

        Optional<PublicModelStatsResponse> result = publicModelService.getModelStats(
                MODEL.getBrand().getDisplayString(), MODEL.getDisplayName(), false);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().uniqueCars(),
                "Two distinct cars should be counted, regardless of user count");
    }

    @Test
    void getModelStats_oneCarsWithMultipleLogs_uniqueCarsIsOne() {
        User user = createAndSaveUser("uniquecars3-" + System.currentTimeMillis() + "@example.com");
        Car car = createAndSaveCar(user.getId(), MODEL);

        saveLog(car.getId());
        saveLog(car.getId());
        saveLog(car.getId());

        Optional<PublicModelStatsResponse> result = publicModelService.getModelStats(
                MODEL.getBrand().getDisplayString(), MODEL.getDisplayName(), false);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().uniqueCars(),
                "Multiple logs from one car should count as one unique car");
    }

    @Test
    void getModelStats_carsFromDifferentYears_yearDistributionReflectsActualYears() {
        User user = createAndSaveUser("yeardist1-" + System.currentTimeMillis() + "@example.com");

        Car car2021 = carRepository.save(Car.createNew(
                user.getId(), MODEL, 2021,
                "LP-" + UUID.randomUUID().toString().substring(0, 8),
                null, new BigDecimal("77.0"), new BigDecimal("420.0"), null));
        Car car2023a = carRepository.save(Car.createNew(
                user.getId(), MODEL, 2023,
                "LP-" + UUID.randomUUID().toString().substring(0, 8),
                null, new BigDecimal("77.0"), new BigDecimal("420.0"), null));
        Car car2023b = carRepository.save(Car.createNew(
                user.getId(), MODEL, 2023,
                "LP-" + UUID.randomUUID().toString().substring(0, 8),
                null, new BigDecimal("77.0"), new BigDecimal("420.0"), null));

        saveLog(car2021.getId());
        saveLog(car2023a.getId());
        saveLog(car2023b.getId());

        Optional<PublicModelStatsResponse> result = publicModelService.getModelStats(
                MODEL.getBrand().getDisplayString(), MODEL.getDisplayName(), false);

        assertTrue(result.isPresent());
        List<PublicModelStatsResponse.YearEntry> dist = result.get().yearDistribution();
        assertNotNull(dist, "yearDistribution must not be null");

        var entry2021 = dist.stream().filter(e -> e.year() == 2021).findFirst();
        var entry2023 = dist.stream().filter(e -> e.year() == 2023).findFirst();

        assertTrue(entry2021.isPresent(), "2021 must appear in distribution");
        assertTrue(entry2023.isPresent(), "2023 must appear in distribution");
        assertEquals(1, entry2021.get().carCount());
        assertEquals(2, entry2023.get().carCount());
    }

    private void saveLog(UUID carId) {
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
                null, null,
                false, null
        ));
    }
}
