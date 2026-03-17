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
 * Integration tests for PublicModelService.getTopModels() — specifically the
 * per-variant real consumption range (minRealConsumption / maxRealConsumption).
 *
 * Uses only Polestar models since no other test class touches them,
 * which means zero data pollution and exact value assertions are reliable.
 *
 * Range logic under test:
 * - Only variants with >= 100 trips qualify
 * - Range is set only when >= 2 variants qualify (otherwise null)
 * - minReal < maxReal when set
 *
 * Consumption is verified via the no-SoC fallback path:
 *   kWhCharged / distanceKm * 100 = kWh/100km
 * → 101 logs at 100 km intervals with kWhCharged = X → exactly X kWh/100km per trip.
 */
class PublicModelServiceTopModelsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublicModelService publicModelService;

    @Autowired
    private CacheManager cacheManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("topModels").clear();
        User user = createAndSaveUser("topmodels-" + System.currentTimeMillis() + "@example.com");
        userId = user.getId();
    }

    // --- Two qualifying variants → range is set with correct values ---

    @Test
    void getTopModels_twoVariantsEachWith100Trips_setsCorrectMinMaxRealConsumption() {
        // POLESTAR_2, variant 67.0 kWh: 101 logs at 18.0 kWh/100km → 100 trips, avg = 18.0
        saveWltpSpec(CarBrand.CarModel.POLESTAR_2, new BigDecimal("67.0"), new BigDecimal("16.8"));
        Car car67 = createCarWithBattery(CarBrand.CarModel.POLESTAR_2, new BigDecimal("67.0"));
        saveLogsForCar(car67.getId(), 101, new BigDecimal("18.0"));

        // POLESTAR_2, variant 82.0 kWh: 101 logs at 14.0 kWh/100km → 100 trips, avg = 14.0
        saveWltpSpec(CarBrand.CarModel.POLESTAR_2, new BigDecimal("82.0"), new BigDecimal("14.5"));
        Car car82 = createCarWithBattery(CarBrand.CarModel.POLESTAR_2, new BigDecimal("82.0"));
        saveLogsForCar(car82.getId(), 101, new BigDecimal("14.0"));

        List<TopModelResponse> results = publicModelService.getTopModels(20, false);
        TopModelResponse polestar2 = findModel(results, "POLESTAR_2");

        assertNotNull(polestar2, "POLESTAR_2 should appear in top models");
        assertNotNull(polestar2.minRealConsumptionKwhPer100km(), "minReal should be set");
        assertNotNull(polestar2.maxRealConsumptionKwhPer100km(), "maxReal should be set");
        assertEquals(new BigDecimal("14.0"), polestar2.minRealConsumptionKwhPer100km(),
                "82 kWh variant (14.0 kWh/100km) should be minReal");
        assertEquals(new BigDecimal("18.0"), polestar2.maxRealConsumptionKwhPer100km(),
                "67 kWh variant (18.0 kWh/100km) should be maxReal");
    }

    // --- Only one variant qualifies → no range ---

    @Test
    void getTopModels_onlyOneVariantWith100Trips_returnsNullRange() {
        // POLESTAR_3, variant 107.0 kWh: 101 logs → qualifies
        saveWltpSpec(CarBrand.CarModel.POLESTAR_3, new BigDecimal("107.0"), new BigDecimal("21.0"));
        Car car107 = createCarWithBattery(CarBrand.CarModel.POLESTAR_3, new BigDecimal("107.0"));
        saveLogsForCar(car107.getId(), 101, new BigDecimal("22.0"));

        // POLESTAR_3, variant 120.0 kWh: only 5 logs → 4 trips, does not qualify
        saveWltpSpec(CarBrand.CarModel.POLESTAR_3, new BigDecimal("120.0"), new BigDecimal("22.0"));
        Car car120 = createCarWithBattery(CarBrand.CarModel.POLESTAR_3, new BigDecimal("120.0"));
        saveLogsForCar(car120.getId(), 5, new BigDecimal("20.0"));

        List<TopModelResponse> results = publicModelService.getTopModels(20, false);
        TopModelResponse polestar3 = findModel(results, "POLESTAR_3");

        assertNotNull(polestar3, "POLESTAR_3 should appear in top models");
        assertNotNull(polestar3.avgConsumptionKwhPer100km(),
                "overall avg consumption must still be set even without a range");
        assertNull(polestar3.minRealConsumptionKwhPer100km(),
                "minReal must be null — only 1 of 2 variants has >= 100 trips");
        assertNull(polestar3.maxRealConsumptionKwhPer100km(),
                "maxReal must be null — only 1 of 2 variants has >= 100 trips");
    }

    // --- Boundary: exactly 100 trips vs. 99 trips ---

    @Test
    void getTopModels_exactlyAtThresholdBoundary_onlyQualifyingVariantCountsForRange() {
        // POLESTAR_4, variant 94.0 kWh: 101 logs → exactly 100 trips (meets threshold)
        saveWltpSpec(CarBrand.CarModel.POLESTAR_4, new BigDecimal("94.0"), new BigDecimal("19.5"));
        Car car94 = createCarWithBattery(CarBrand.CarModel.POLESTAR_4, new BigDecimal("94.0"));
        saveLogsForCar(car94.getId(), 101, new BigDecimal("20.0")); // 101 logs → 100 trips

        // POLESTAR_4, variant 110.0 kWh: 100 logs → exactly 99 trips (one below threshold)
        saveWltpSpec(CarBrand.CarModel.POLESTAR_4, new BigDecimal("110.0"), new BigDecimal("18.0"));
        Car car110 = createCarWithBattery(CarBrand.CarModel.POLESTAR_4, new BigDecimal("110.0"));
        saveLogsForCar(car110.getId(), 100, new BigDecimal("18.0")); // 100 logs → 99 trips

        List<TopModelResponse> results = publicModelService.getTopModels(20, false);
        TopModelResponse polestar4 = findModel(results, "POLESTAR_4");

        assertNotNull(polestar4, "POLESTAR_4 should appear in top models");
        assertNull(polestar4.minRealConsumptionKwhPer100km(),
                "minReal must be null — only 1 variant qualifies (99 trips < threshold)");
        assertNull(polestar4.maxRealConsumptionKwhPer100km(),
                "maxReal must be null — only 1 variant qualifies (99 trips < threshold)");
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
                "P-" + UUID.randomUUID().toString().substring(0, 8),
                "Test Trim", batteryKwh, new BigDecimal("200.0"), null
        ));
    }

    /**
     * Creates N logs at 100 km intervals with the given kWh per 100 km (no-SoC fallback path).
     * N logs → N-1 trips. Fallback: consumption = kWhCharged / distanceKm * 100.
     * With distanceKm = 100 and kWhCharged = X → consumption = X kWh/100km exactly.
     * Use N=101 for exactly 100 trips (the threshold), N=100 for 99 trips.
     */
    private void saveLogsForCar(UUID carId, int logCount, BigDecimal kwhPer100km) {
        for (int i = 0; i < logCount; i++) {
            evLogRepository.save(EvLog.createNew(
                    carId,
                    kwhPer100km,              // kWhCharged = kWhPer100km → consumption = kWhPer100km per 100km
                    new BigDecimal("10.00"),
                    30,
                    "u33d1",
                    10000 + (i * 100),        // 100 km between each log
                    new BigDecimal("11.0"),
                    null,                     // no SoC → fallback path
                    LocalDateTime.now().minusDays(logCount - i),
                    ChargingType.UNKNOWN,
                    null, null
            ));
        }
    }

    private TopModelResponse findModel(List<TopModelResponse> results, String modelEnum) {
        return results.stream()
                .filter(r -> r.model().equals(modelEnum))
                .findFirst()
                .orElse(null);
    }
}
