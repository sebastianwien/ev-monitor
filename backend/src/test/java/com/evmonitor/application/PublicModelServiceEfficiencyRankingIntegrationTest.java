package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PublicModelService.getMostEfficientModels().
 *
 * Each test uses a unique (model, batteryKwh) combination to avoid DB conflicts
 * — data is not rolled back between tests (AbstractIntegrationTest has no @Transactional).
 *
 * Rules under test:
 * - Models without avgConsumptionKwhPer100km are excluded
 * - Models with logCount < 10 are excluded
 * - Results are sorted ascending by avgConsumptionKwhPer100km (most efficient first)
 */
class PublicModelServiceEfficiencyRankingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublicModelService publicModelService;

    @Autowired
    private CacheManager cacheManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("efficientModels").clear();
        cacheManager.getCache("topModels").clear();
        userId = createAndSaveUser("efficiency-" + System.currentTimeMillis() + "@example.com").getId();
    }

    @Test
    void shouldExcludeModelsWithoutAnyLogs() {
        // XPENG_G6 with 66.0 kWh: WLTP spec exists, but no cars/logs → not in results
        saveWltpSpec(CarBrand.CarModel.XPENG_G6, new BigDecimal("66.0"), new BigDecimal("16.0"));

        List<TopModelResponse> results = publicModelService.getMostEfficientModels(10, false);
        boolean hasXpengG6WithZeroLogs = results.stream()
                .anyMatch(r -> r.model().equals("XPENG_G6") && r.logCount() == 0);
        assertFalse(hasXpengG6WithZeroLogs, "XPENG_G6 with 0 logs should not appear in efficiency ranking");
    }

    @Test
    void shouldExcludeModelsWithFewerThan10Logs() {
        // XPENG_G9 with 78.0 kWh: only 5 logs → logCount < 10 → filtered out
        saveWltpSpec(CarBrand.CarModel.XPENG_G9, new BigDecimal("78.0"), new BigDecimal("17.0"));
        Car car = createCarWithBattery(CarBrand.CarModel.XPENG_G9, new BigDecimal("78.0"));
        saveLogsForCar(car.getId(), 5, new BigDecimal("17.0"));

        List<TopModelResponse> results = publicModelService.getMostEfficientModels(10, false);
        boolean hasXpengG9WithFewLogs = results.stream()
                .anyMatch(r -> r.model().equals("XPENG_G9") && r.logCount() < 10);
        assertFalse(hasXpengG9WithFewLogs, "XPENG_G9 with < 10 logs should be excluded from efficiency ranking");
    }

    @Test
    void shouldIncludeModelWithAtLeast10Logs() {
        // XPENG_P7 with 60.0 kWh: 11 logs → 10 trips — exactly at threshold → should be included
        saveWltpSpec(CarBrand.CarModel.XPENG_P7, new BigDecimal("60.0"), new BigDecimal("15.5"));
        Car car = createCarWithBattery(CarBrand.CarModel.XPENG_P7, new BigDecimal("60.0"));
        saveLogsForCar(car.getId(), 11, new BigDecimal("15.5")); // 11 logs → 10 trips

        List<TopModelResponse> results = publicModelService.getMostEfficientModels(10, false);
        boolean hasXpengP7 = results.stream().anyMatch(r -> r.model().equals("XPENG_P7"));
        assertTrue(hasXpengP7, "XPENG_P7 with exactly 10 trips (11 logs) should be included");
    }

    @Test
    void shouldSortByConsumptionAscending() {
        // XPENG_G6 with 70.0 kWh: 12 logs at 18.0 kWh/100km (less efficient)
        saveWltpSpec(CarBrand.CarModel.XPENG_G6, new BigDecimal("70.0"), new BigDecimal("17.0"));
        Car carG6 = createCarWithBattery(CarBrand.CarModel.XPENG_G6, new BigDecimal("70.0"));
        saveLogsForCar(carG6.getId(), 12, new BigDecimal("18.0"));

        // XPENG_P7_PLUS with 85.0 kWh: 12 logs at 14.0 kWh/100km (more efficient)
        saveWltpSpec(CarBrand.CarModel.XPENG_P7_PLUS, new BigDecimal("85.0"), new BigDecimal("15.0"));
        Car carP7Plus = createCarWithBattery(CarBrand.CarModel.XPENG_P7_PLUS, new BigDecimal("85.0"));
        saveLogsForCar(carP7Plus.getId(), 12, new BigDecimal("14.0"));

        List<TopModelResponse> results = publicModelService.getMostEfficientModels(10, false);

        int g6Index = -1;
        int p7PlusIndex = -1;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).model().equals("XPENG_G6")) g6Index = i;
            if (results.get(i).model().equals("XPENG_P7_PLUS")) p7PlusIndex = i;
        }

        assertTrue(g6Index >= 0, "XPENG_G6 should appear in results");
        assertTrue(p7PlusIndex >= 0, "XPENG_P7_PLUS should appear in results");
        assertTrue(p7PlusIndex < g6Index,
                "XPENG_P7_PLUS (14.0 kWh/100km) should rank before XPENG_G6 (18.0 kWh/100km)");
    }

    // --- Helpers ---

    private void saveWltpSpec(CarBrand.CarModel carModel, BigDecimal batteryKwh, BigDecimal wltpConsumption) {
        vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                carModel.getBrand().name(),
                carModel.name(),
                batteryKwh,
                new BigDecimal("400.0"),
                wltpConsumption,
                VehicleSpecification.WltpType.COMBINED
        ));
    }

    private Car createCarWithBattery(CarBrand.CarModel model, BigDecimal batteryKwh) {
        return carRepository.save(Car.createNew(
                userId, model, 2023,
                "EFF-" + UUID.randomUUID().toString().substring(0, 8),
                "Test Trim", batteryKwh, new BigDecimal("200.0"), null
        ));
    }

    private void saveLogsForCar(UUID carId, int logCount, BigDecimal kwhPer100km) {
        for (int i = 0; i < logCount; i++) {
            evLogRepository.save(EvLog.createNew(
                    carId,
                    kwhPer100km,
                    new BigDecimal("10.00"),
                    30,
                    "u33d1",
                    10000 + (i * 100),
                    new BigDecimal("11.0"),
                    null,
                    LocalDateTime.now().minusDays(logCount - i),
                    ChargingType.UNKNOWN,
                    null, null,
                    false, null
            ));
        }
    }
}
