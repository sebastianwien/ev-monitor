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
 * Integration tests for PublicModelService.getTopModels() - specifically the
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

    /** Large enough that ranking position by log count never hides the model under test. */
    private static final int ALL_MODELS = 500;

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
                "minReal must be null - only 1 of 2 variants has >= 100 trips");
        assertNull(polestar3.maxRealConsumptionKwhPer100km(),
                "maxReal must be null - only 1 of 2 variants has >= 100 trips");
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
                "minReal must be null - only 1 variant qualifies (99 trips < threshold)");
        assertNull(polestar4.maxRealConsumptionKwhPer100km(),
                "maxReal must be null - only 1 variant qualifies (99 trips < threshold)");
    }

    // --- Placeholder brand "Andere Marke" never shows up in rankings ---

    @Test
    void getTopModels_excludesSonstigeBrand() {
        saveWltpSpec(CarBrand.CarModel.SONSTIGE_CUSTOM, new BigDecimal("60.0"), new BigDecimal("17.0"));
        Car custom = createCarWithBattery(CarBrand.CarModel.SONSTIGE_CUSTOM, new BigDecimal("60.0"));
        saveLogsForCar(custom.getId(), 101, new BigDecimal("17.0"));

        List<TopModelResponse> results = publicModelService.getTopModels(ALL_MODELS, false);

        assertTrue(results.stream().noneMatch(r -> r.brand().equals(CarBrand.SONSTIGE.name())),
                "placeholder brand SONSTIGE must not appear in top models");
    }

    // --- Seasonal consumption (summer May-Aug, winter Nov-Feb via EvLogService) ---

    @Test
    void getTopModels_withSummerAndWinterLogs_returnsSeasonalConsumption() {
        // GV60: 11 logs in July 2025 at 15.0 → 10 summer trips, then 10 logs in January 2026
        // at 20.0 → 10 winter trips (fallback: own kWhCharged / distance since previous log)
        saveWltpSpec(CarBrand.CarModel.GV60, new BigDecimal("77.4"), new BigDecimal("17.0"));
        Car car = createCarWithBattery(CarBrand.CarModel.GV60, new BigDecimal("77.4"));
        saveLogsForCarFrom(car.getId(), 20000, 11, new BigDecimal("15.0"), LocalDateTime.of(2025, 7, 1, 12, 0));
        saveLogsForCarFrom(car.getId(), 21100, 10, new BigDecimal("20.0"), LocalDateTime.of(2026, 1, 5, 12, 0));

        TopModelResponse gv60 = findModel(publicModelService.getTopModels(ALL_MODELS, false), "GV60");

        assertNotNull(gv60, "GV60 should appear in top models");
        assertEquals(new BigDecimal("15.0"), gv60.summerConsumptionKwhPer100km());
        assertEquals(new BigDecimal("20.0"), gv60.winterConsumptionKwhPer100km());
    }

    @Test
    void getTopModels_withoutWinterLogs_returnsNullWinter() {
        saveWltpSpec(CarBrand.CarModel.GV70_ELECTRIFIED, new BigDecimal("77.4"), new BigDecimal("19.0"));
        Car car = createCarWithBattery(CarBrand.CarModel.GV70_ELECTRIFIED, new BigDecimal("77.4"));
        saveLogsForCarFrom(car.getId(), 30000, 11, new BigDecimal("18.0"), LocalDateTime.of(2025, 6, 1, 12, 0));

        TopModelResponse gv70 = findModel(publicModelService.getTopModels(ALL_MODELS, false), "GV70_ELECTRIFIED");

        assertNotNull(gv70, "GV70_ELECTRIFIED should appear in top models");
        assertEquals(new BigDecimal("18.0"), gv70.summerConsumptionKwhPer100km());
        assertNull(gv70.winterConsumptionKwhPer100km(), "no winter trips → winter must be null");
    }

    // --- WLTP average across spec variants ---

    @Test
    void getTopModels_returnsAverageOfWltpVariants() {
        // (16.00 + 17.00 + 20.50) / 3 = 17.83 → 17.8
        saveWltpSpec(CarBrand.CarModel.ELETRE, new BigDecimal("100.0"), new BigDecimal("16.00"));
        saveWltpSpec(CarBrand.CarModel.ELETRE, new BigDecimal("105.0"), new BigDecimal("17.00"));
        saveWltpSpec(CarBrand.CarModel.ELETRE, new BigDecimal("112.0"), new BigDecimal("20.50"));
        Car car = createCarWithBattery(CarBrand.CarModel.ELETRE, new BigDecimal("105.0"));
        saveLogsForCar(car.getId(), 11, new BigDecimal("22.0"));

        TopModelResponse eletre = findModel(publicModelService.getTopModels(ALL_MODELS, false), "ELETRE");

        assertNotNull(eletre, "ELETRE should appear in top models");
        assertEquals(new BigDecimal("17.8"), eletre.avgWltpConsumptionKwhPer100km());
        assertEquals(0, new BigDecimal("16.00").compareTo(eletre.minWltpConsumptionKwhPer100km()));
        assertEquals(0, new BigDecimal("20.50").compareTo(eletre.maxWltpConsumptionKwhPer100km()));
    }

    // --- Data basis: drivers and cars behind the numbers (Kaufhilfe Phase A) ---

    @Test
    void getTopModels_returnsContributorAndCarCount() {
        // MACAN_ELECTRIC: two cars of one driver plus one car of a second driver → 2 drivers, 3 cars
        saveWltpSpec(CarBrand.CarModel.MACAN_ELECTRIC, new BigDecimal("95.0"), new BigDecimal("20.0"));
        Car first = createCarWithBattery(CarBrand.CarModel.MACAN_ELECTRIC, new BigDecimal("95.0"));
        Car second = createCarWithBattery(CarBrand.CarModel.MACAN_ELECTRIC, new BigDecimal("95.0"));
        User otherDriver = createAndSaveUser("topmodels-other-" + System.currentTimeMillis() + "@example.com");
        Car third = createCarForUser(otherDriver.getId(), CarBrand.CarModel.MACAN_ELECTRIC, new BigDecimal("95.0"));
        saveLogsForCar(first.getId(), 3, new BigDecimal("20.0"));
        saveLogsForCar(second.getId(), 3, new BigDecimal("20.0"));
        saveLogsForCar(third.getId(), 3, new BigDecimal("20.0"));

        TopModelResponse macan = findModel(publicModelService.getTopModels(ALL_MODELS, false), "MACAN_ELECTRIC");

        assertNotNull(macan, "MACAN_ELECTRIC should appear in top models");
        assertEquals(2, macan.contributorCount());
        assertEquals(3, macan.carCount());
    }

    @Test
    void getTopModels_returnsMinAndMaxNetCapacityFromWltpSpecs() {
        // MEGANE_E_TECH: two WLTP specs, net 40.0 and 60.0 kWh (gross 42.0 and 63.0)
        saveWltpSpecWithNet(CarBrand.CarModel.MEGANE_E_TECH, new BigDecimal("42.0"), new BigDecimal("40.0"), new BigDecimal("15.5"));
        saveWltpSpecWithNet(CarBrand.CarModel.MEGANE_E_TECH, new BigDecimal("63.0"), new BigDecimal("60.0"), new BigDecimal("16.1"));
        Car car = createCarWithBattery(CarBrand.CarModel.MEGANE_E_TECH, new BigDecimal("63.0"));
        saveLogsForCar(car.getId(), 3, new BigDecimal("16.0"));

        TopModelResponse megane = findModel(publicModelService.getTopModels(ALL_MODELS, false), "MEGANE_E_TECH");

        assertNotNull(megane, "MEGANE_E_TECH should appear in top models");
        assertEquals(0, new BigDecimal("40.0").compareTo(megane.minNetCapacityKwh()));
        assertEquals(0, new BigDecimal("60.0").compareTo(megane.maxNetCapacityKwh()));
    }

    // --- Range span for the needs check (Kaufhilfe Phase B): net capacity × 100 / consumption ---

    @Test
    void getTopModels_derivesTypicalAndWinterRangeFromSmallestAndLargestBattery() {
        // G80_ELECTRIFIED, net 60.0 and 75.0: 10 summer trips at 15.0 and 10 winter trips at 20.0
        // → overall Ø 17.5, winter 20.0. Typical: 60 × 100 / 17.5 = 343, 75 × 100 / 17.5 = 429.
        // Winter: 60 × 100 / 20 = 300, 75 × 100 / 20 = 375.
        saveWltpSpecWithNet(CarBrand.CarModel.G80_ELECTRIFIED, new BigDecimal("65.0"), new BigDecimal("60.0"), new BigDecimal("19.0"));
        saveWltpSpecWithNet(CarBrand.CarModel.G80_ELECTRIFIED, new BigDecimal("80.0"), new BigDecimal("75.0"), new BigDecimal("19.5"));
        Car car = createCarWithBattery(CarBrand.CarModel.G80_ELECTRIFIED, new BigDecimal("65.0"));
        saveLogsForCarFrom(car.getId(), 40000, 11, new BigDecimal("15.0"), LocalDateTime.of(2025, 7, 1, 12, 0));
        saveLogsForCarFrom(car.getId(), 41100, 10, new BigDecimal("20.0"), LocalDateTime.of(2026, 1, 5, 12, 0));

        TopModelResponse g80 = findModel(publicModelService.getTopModels(ALL_MODELS, false), "G80_ELECTRIFIED");

        assertNotNull(g80, "G80_ELECTRIFIED should appear in top models");
        assertEquals(new BigDecimal("17.5"), g80.avgConsumptionKwhPer100km());
        assertEquals(343, g80.typicalRangeMinKm());
        assertEquals(429, g80.typicalRangeMaxKm());
        assertEquals(300, g80.winterRangeMinKm());
        assertEquals(375, g80.winterRangeMaxKm());
    }

    @Test
    void getTopModels_singleVariantWithoutWinterLogs_returnsEqualTypicalRangeAndNullWinterRange() {
        // E_TRON_GT: one spec (net 84.0), logs outside the seasons → typical 84 × 100 / 20 = 420, no winter
        saveWltpSpecWithNet(CarBrand.CarModel.E_TRON_GT, new BigDecimal("93.4"), new BigDecimal("84.0"), new BigDecimal("20.5"));
        Car car = createCarWithBattery(CarBrand.CarModel.E_TRON_GT, new BigDecimal("93.4"));
        saveLogsForCarFrom(car.getId(), 50000, 6, new BigDecimal("20.0"), LocalDateTime.of(2025, 10, 1, 12, 0));

        TopModelResponse etron = findModel(publicModelService.getTopModels(ALL_MODELS, false), "E_TRON_GT");

        assertNotNull(etron, "E_TRON_GT should appear in top models");
        assertEquals(420, etron.typicalRangeMinKm());
        assertEquals(420, etron.typicalRangeMaxKm());
        assertNull(etron.winterRangeMinKm(), "no winter trips → winter range must be null");
        assertNull(etron.winterRangeMaxKm(), "no winter trips → winter range must be null");
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

    private void saveWltpSpecWithNet(CarBrand.CarModel carModel, BigDecimal grossKwh, BigDecimal netKwh, BigDecimal wltpConsumption) {
        vehicleSpecificationRepository.save(VehicleSpecification.createNew(
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

    private Car createCarWithBattery(CarBrand.CarModel model, BigDecimal batteryKwh) {
        return createCarForUser(userId, model, batteryKwh);
    }

    private Car createCarForUser(UUID ownerId, CarBrand.CarModel model, BigDecimal batteryKwh) {
        return carRepository.save(Car.createNew(
                ownerId, model, 2023,
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
                    null, null,
                    false, null
            ));
        }
    }

    /**
     * Creates N logs one day apart starting at {@code start}, 100 km between logs,
     * so every log after the first closes a 100 km trip at exactly kwhPer100km.
     */
    private void saveLogsForCarFrom(UUID carId, int startOdometerKm, int logCount,
                                    BigDecimal kwhPer100km, LocalDateTime start) {
        for (int i = 0; i < logCount; i++) {
            evLogRepository.save(EvLog.createNew(
                    carId,
                    kwhPer100km,
                    new BigDecimal("10.00"),
                    30,
                    "u33d1",
                    startOdometerKm + (i * 100),
                    new BigDecimal("11.0"),
                    null,
                    start.plusDays(i),
                    ChargingType.UNKNOWN,
                    null, null,
                    false, null
            ));
        }
    }

    // --- Average DC charging power (energy-weighted) rides along for the one-stop range ---

    @Test
    void getTopModels_carriesEnergyWeightedDcPower_onlyFromFiveDcSessions() {
        // IONIQ_6: 5 DC sessions, 40 kWh in 30 min each → 80 kW; AC sessions do not count
        saveWltpSpec(CarBrand.CarModel.IONIQ_6, new BigDecimal("77.4"), new BigDecimal("15.0"));
        Car car = createCarWithBattery(CarBrand.CarModel.IONIQ_6, new BigDecimal("77.4"));
        saveLogsForCar(car.getId(), 3, new BigDecimal("16.0"));
        for (int i = 0; i < 5; i++) {
            evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal("40.0"), new BigDecimal("30.00"), 30,
                    "u33d1", 20000 + i * 300, new BigDecimal("150.0"), null,
                    LocalDateTime.now().minusDays(40 + i), ChargingType.DC, null, null, true, null));
        }

        TopModelResponse ioniq6 = findModel(publicModelService.getTopModels(ALL_MODELS, false), "IONIQ_6");
        assertNotNull(ioniq6);
        assertEquals(new BigDecimal("80.0"), ioniq6.avgDcChargingPowerKw());

        // EQS: four DC sessions are below the noise guard → null
        cacheManager.getCache("topModels").clear();
        saveWltpSpec(CarBrand.CarModel.EQS, new BigDecimal("108.4"), new BigDecimal("19.0"));
        Car eqs = createCarWithBattery(CarBrand.CarModel.EQS, new BigDecimal("108.4"));
        saveLogsForCar(eqs.getId(), 3, new BigDecimal("20.0"));
        for (int i = 0; i < 4; i++) {
            evLogRepository.save(EvLog.createNew(eqs.getId(), new BigDecimal("40.0"), new BigDecimal("30.00"), 30,
                    "u33d1", 20000 + i * 300, new BigDecimal("150.0"), null,
                    LocalDateTime.now().minusDays(40 + i), ChargingType.DC, null, null, true, null));
        }
        assertNull(findModel(publicModelService.getTopModels(ALL_MODELS, false), "EQS").avgDcChargingPowerKw());
    }

    private TopModelResponse findModel(List<TopModelResponse> results, String modelEnum) {
        return results.stream()
                .filter(r -> r.model().equals(modelEnum))
                .findFirst()
                .orElse(null);
    }
}
