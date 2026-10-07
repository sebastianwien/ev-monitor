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
 * Integration tests for PublicModelService.getModelsWithoutData(): models that have a WLTP
 * spec but no community logs yet. They are listed with their spec values so the model
 * overview can show them below the ranking ("Noch ohne Fahrerdaten").
 *
 * Uses models no other test class logs against (EMEYA, Q8_E_TRON), so the exclusion
 * assertions are not disturbed by shared test data.
 */
class PublicModelServiceWithoutDataIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublicModelService publicModelService;

    @Autowired
    private CacheManager cacheManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("modelsWithoutData").clear();
        userId = createAndSaveUser("withoutdata-" + System.currentTimeMillis() + "@example.com").getId();
    }

    @Test
    void getModelsWithoutData_listsWltpModelsWithoutLogs() {
        saveWltpSpec(CarBrand.CarModel.EMEYA, new BigDecimal("102.0"), new BigDecimal("98.0"), new BigDecimal("18.0"));
        saveWltpSpec(CarBrand.CarModel.EMEYA, new BigDecimal("110.0"), new BigDecimal("105.0"), new BigDecimal("20.0"));

        ModelWithoutDataResponse emeya = find(publicModelService.getModelsWithoutData(false), "EMEYA");

        assertNotNull(emeya, "EMEYA has a WLTP spec and no logs, must be listed");
        assertEquals("LOTUS", emeya.brand());
        assertEquals("Lotus", emeya.brandDisplayName());
        assertEquals("Lotus Emeya", emeya.modelDisplayName());
        assertEquals("Emeya", emeya.modelUrlSlug());
        assertNotNull(emeya.category());
        assertNotNull(emeya.categoryDisplayName());
        assertEquals(0, new BigDecimal("18.0").compareTo(emeya.minWltpConsumptionKwhPer100km()));
        assertEquals(new BigDecimal("19.0"), emeya.avgWltpConsumptionKwhPer100km());
        assertEquals(0, new BigDecimal("20.0").compareTo(emeya.maxWltpConsumptionKwhPer100km()));
        assertEquals(0, new BigDecimal("98.0").compareTo(emeya.minNetCapacityKwh()));
        assertEquals(0, new BigDecimal("105.0").compareTo(emeya.maxNetCapacityKwh()));
    }

    @Test
    void getModelsWithoutData_excludesModelsWithLogs() {
        saveWltpSpec(CarBrand.CarModel.Q8_E_TRON, new BigDecimal("114.0"), new BigDecimal("106.0"), new BigDecimal("22.0"));
        Car car = carRepository.save(Car.createNew(
                userId, CarBrand.CarModel.Q8_E_TRON, 2024,
                "Q8-" + UUID.randomUUID().toString().substring(0, 8),
                "Test Trim", new BigDecimal("114.0"), new BigDecimal("200.0"), null));
        evLogRepository.save(EvLog.createNew(
                car.getId(), new BigDecimal("30.0"), new BigDecimal("10.00"), 30, "u33d1",
                12000, new BigDecimal("11.0"), null, LocalDateTime.now().minusDays(1),
                ChargingType.UNKNOWN, null, null, false, null));

        assertNull(find(publicModelService.getModelsWithoutData(false), "Q8_E_TRON"),
                "a model with at least one log belongs to the ranking, not to this list");
    }

    @Test
    void getModelsWithoutData_excludesSonstige() {
        saveWltpSpec(CarBrand.CarModel.SONSTIGE_CUSTOM, new BigDecimal("60.0"), new BigDecimal("58.0"), new BigDecimal("17.0"));

        List<ModelWithoutDataResponse> result = publicModelService.getModelsWithoutData(false);

        assertTrue(result.stream().noneMatch(r -> r.brand().equals(CarBrand.SONSTIGE.name())),
                "placeholder brand SONSTIGE must not be listed");
    }

    private void saveWltpSpec(CarBrand.CarModel carModel, BigDecimal grossKwh, BigDecimal netKwh, BigDecimal wltpConsumption) {
        vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                carModel.getBrand().name(), carModel.name(), grossKwh, netKwh,
                new BigDecimal("400.0"), wltpConsumption,
                VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.WLTP));
    }

    private ModelWithoutDataResponse find(List<ModelWithoutDataResponse> list, String modelEnum) {
        return list.stream().filter(r -> r.model().equals(modelEnum)).findFirst().orElse(null);
    }
}
