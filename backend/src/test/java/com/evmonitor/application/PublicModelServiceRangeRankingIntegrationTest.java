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
 * Integration tests for PublicModelService.getLongestRangeModels().
 *
 * Each test uses a unique (model, batteryKwh) combination to avoid DB conflicts
 * — data is not rolled back between tests (AbstractIntegrationTest has no @Transactional).
 *
 * Rules under test:
 * - Real range = nominal net capacity / real consumption × 100, per variant (best variant wins).
 * - Only variants with >= 100 trips qualify (noise guard), like the per-variant consumption gate.
 * - Models without a qualifying variant get realRangeKm == null and are excluded.
 * - Results are sorted descending by realRangeKm (longest range first).
 */
class PublicModelServiceRangeRankingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublicModelService publicModelService;

    @Autowired
    private CacheManager cacheManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("longestRangeModels").clear();
        cacheManager.getCache("topModels").clear();
        userId = createAndSaveUser("range-" + System.currentTimeMillis() + "@example.com").getId();
    }

    @Test
    void shouldExcludeModelsWithFewerThan100Trips() {
        // XPENG_G6 @ 88.5 kWh: only 11 logs → 10 trips → variant has no real range → excluded
        saveWltpSpec(CarBrand.CarModel.XPENG_G6, new BigDecimal("88.5"), new BigDecimal("15.0"));
        Car car = createCarWithBattery(CarBrand.CarModel.XPENG_G6, new BigDecimal("88.5"));
        saveLogsForCar(car.getId(), 11, new BigDecimal("15.0"));

        List<TopModelResponse> results = publicModelService.getLongestRangeModels(50, false);
        assertTrue(results.stream().noneMatch(r -> r.model().equals("XPENG_G6")),
                "XPENG_G6 with < 100 trips should be excluded from the range ranking");
    }

    @Test
    void shouldIncludeModelWithEnoughTripsAndComputeRange() {
        // XPENG_G9 @ 90.0 kWh, 15.0 kWh/100km, 102 logs → 101 trips → range ~600 km
        saveWltpSpec(CarBrand.CarModel.XPENG_G9, new BigDecimal("90.0"), new BigDecimal("15.0"));
        Car car = createCarWithBattery(CarBrand.CarModel.XPENG_G9, new BigDecimal("90.0"));
        saveLogsForCar(car.getId(), 102, new BigDecimal("15.0"));

        List<TopModelResponse> results = publicModelService.getLongestRangeModels(50, false);
        TopModelResponse g9 = results.stream()
                .filter(r -> r.model().equals("XPENG_G9")).findFirst().orElse(null);

        assertNotNull(g9, "XPENG_G9 with 101 trips should appear in the range ranking");
        assertNotNull(g9.realRangeKm(), "realRangeKm must be populated");
        assertTrue(g9.realRangeKm().signum() > 0, "realRangeKm must be positive");
    }

    @Test
    void shouldComputeRangeForSpecLinkedCarsWithoutCustomCapacity() {
        // Real-world dominant case: cars are linked to their variant via vehicleSpecificationId,
        // and custom_net_capacity_kwh is null (~93% of production cars). The range computation
        // must match these cars via the spec link, not only via the custom-capacity override.
        VehicleSpecification spec = saveWltpSpecReturning(
                CarBrand.CarModel.NIO_ET7, new BigDecimal("90.0"), new BigDecimal("90.0"), new BigDecimal("15.0"));
        Car car = createCarLinkedToSpec(CarBrand.CarModel.NIO_ET7, spec);
        saveLogsForCar(car.getId(), 102, new BigDecimal("15.0"));

        List<TopModelResponse> results = publicModelService.getLongestRangeModels(50, false);
        TopModelResponse nio = results.stream()
                .filter(r -> r.model().equals("NIO_ET7")).findFirst().orElse(null);

        assertNotNull(nio, "spec-linked NIO_ET7 (custom capacity null) must appear in the range ranking");
        assertNotNull(nio.realRangeKm(), "realRangeKm must be populated for spec-linked cars");
        assertTrue(nio.realRangeKm().signum() > 0, "realRangeKm must be positive");
    }

    @Test
    void shouldSortByRangeDescending() {
        // Big range: XPENG_P7 @ 95.0 kWh, 15.0 kWh/100km → ~633 km
        saveWltpSpec(CarBrand.CarModel.XPENG_P7, new BigDecimal("95.0"), new BigDecimal("15.0"));
        Car carBig = createCarWithBattery(CarBrand.CarModel.XPENG_P7, new BigDecimal("95.0"));
        saveLogsForCar(carBig.getId(), 102, new BigDecimal("15.0"));

        // Small range: XPENG_P7_PLUS @ 60.0 kWh, 20.0 kWh/100km → ~300 km
        saveWltpSpec(CarBrand.CarModel.XPENG_P7_PLUS, new BigDecimal("60.0"), new BigDecimal("20.0"));
        Car carSmall = createCarWithBattery(CarBrand.CarModel.XPENG_P7_PLUS, new BigDecimal("60.0"));
        saveLogsForCar(carSmall.getId(), 102, new BigDecimal("20.0"));

        List<TopModelResponse> results = publicModelService.getLongestRangeModels(50, false);

        int bigIndex = -1, smallIndex = -1;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).model().equals("XPENG_P7")) bigIndex = i;
            if (results.get(i).model().equals("XPENG_P7_PLUS")) smallIndex = i;
        }

        assertTrue(bigIndex >= 0, "XPENG_P7 (big range) should appear");
        assertTrue(smallIndex >= 0, "XPENG_P7_PLUS (small range) should appear");
        assertTrue(bigIndex < smallIndex,
                "XPENG_P7 (~633 km) should rank before XPENG_P7_PLUS (~300 km)");
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

    private VehicleSpecification saveWltpSpecReturning(CarBrand.CarModel carModel, BigDecimal grossKwh,
            BigDecimal netKwh, BigDecimal wltpConsumption) {
        return vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                carModel.getBrand().name(),
                carModel.name(),
                grossKwh,
                netKwh,
                new BigDecimal("400.0"),
                wltpConsumption,
                VehicleSpecification.WltpType.COMBINED,
                VehicleSpecification.RatingSource.WLTP
        ));
    }

    private Car createCarLinkedToSpec(CarBrand.CarModel model, VehicleSpecification spec) {
        // custom capacity null on purpose: the car knows its variant via the spec link
        Car car = Car.createNew(
                userId, model, 2023,
                "RNG-" + UUID.randomUUID().toString().substring(0, 8),
                "Test Trim", null, new BigDecimal("200.0"), null
        ).toBuilder().vehicleSpecificationId(spec.getId()).build();
        return carRepository.save(car);
    }

    private Car createCarWithBattery(CarBrand.CarModel model, BigDecimal batteryKwh) {
        return carRepository.save(Car.createNew(
                userId, model, 2023,
                "RNG-" + UUID.randomUUID().toString().substring(0, 8),
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
