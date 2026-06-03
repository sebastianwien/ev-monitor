package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class EvLogStatisticsPeerModelComparisonTest extends AbstractServiceTest {

    @Autowired
    private EvLogStatisticsService evLogStatisticsService;

    // --- helpers ---

    private VehicleSpecification saveSpec(String brand, String model, String variantName, BigDecimal batteryKwh) {
        VehicleSpecification spec = VehicleSpecification.createNew(
                brand, model, batteryKwh, null,
                new BigDecimal("490"), new BigDecimal("15.4"),
                VehicleSpecification.WltpType.COMBINED,
                VehicleSpecification.RatingSource.WLTP, variantName);
        return vehicleSpecificationRepository.save(spec);
    }

    private Car createCar(UUID userId, CarBrand.CarModel model, UUID specId) {
        Car car = Car.createNew(userId, model, 2022, "TST-1",
                "Standard", new BigDecimal("75.0"), new BigDecimal("490"), null);
        if (specId != null) {
            car = car.toBuilder().vehicleSpecificationId(specId).build();
        }
        return carRepository.save(car);
    }

    private void addLog(UUID carId, double kwh, double distanceKm, double costEur) {
        // Anchor log at km=0 (needed so the second log has a valid distance delta)
        EvLog anchor = EvLog.createNew(carId, new BigDecimal("0.1"), null,
                1, null, 0, null, null,
                LocalDateTime.now().minusDays(2), null, null, null, false, null);
        anchor = anchor.toBuilder().includeInStatistics(true).build();
        evLogRepository.save(anchor);

        EvLog log = EvLog.createNew(carId, new BigDecimal(kwh), new BigDecimal(costEur),
                60, null, (int) distanceKm, null, null,
                LocalDateTime.now().minusDays(1), null, null, null, false, null);
        log = log.toBuilder().includeInStatistics(true).build();
        evLogRepository.save(log);
    }

    // --- tests ---

    @Test
    void getPeerModelComparison_returnsAllSpecsOfSameModel_groupedBySpecId() {
        // Setup: User hat Model 3 2024 LR (75kWh)
        VehicleSpecification userSpec = saveSpec("Tesla", "Model 3", "2024 LR", new BigDecimal("75.0"));
        User user = createUser("test@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, userSpec.getId());

        // User hat 1 log: 20 kWh, 100 km → 20 kWh/100km, 0.30 €/kWh
        addLog(userCar.getId(), 20.0, 100.0, 6.0);

        // Peer 1: gleiche spec (2024 LR) → 22 kWh, 100 km → 22 kWh/100km, 0.28 €/kWh
        User peerUser1 = createUser("peer1@example.com", "DE");
        Car peerCar1Spec1 = createCar(peerUser1.getId(), CarBrand.CarModel.MODEL_3, userSpec.getId());
        addLog(peerCar1Spec1.getId(), 22.0, 100.0, 6.16);

        // Peer 2: andere spec (2025 RWD, 60kWh) → 19 kWh, 100 km → 19 kWh/100km
        VehicleSpecification otherSpec = saveSpec("Tesla", "Model 3", "2025 RWD", new BigDecimal("60.0"));
        User peerUser2 = createUser("peer2@example.com", "DE");
        Car peerCar2Spec2 = createCar(peerUser2.getId(), CarBrand.CarModel.MODEL_3, otherSpec.getId());
        addLog(peerCar2Spec2.getId(), 19.0, 100.0, 5.32);

        // Peer 3: auch 2025 RWD
        User peerUser3 = createUser("peer3@example.com", "DE");
        Car peerCar3Spec2 = createCar(peerUser3.getId(), CarBrand.CarModel.MODEL_3, otherSpec.getId());
        addLog(peerCar3Spec2.getId(), 18.0, 100.0, 5.04);

        // Act
        PeerModelComparisonResponse response = evLogStatisticsService.getPeerModelComparison(userCar, user);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.modelComparisons().size());

        // Item 1: User's spec (2024 LR)
        PeerModelComparisonItem item1 = response.modelComparisons().stream()
                .filter(i -> i.vehicleSpecificationId().equals(userSpec.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(item1.isUserVehicleSpec());
        assertEquals(new BigDecimal("20.00"), item1.userMetrics().consumptionKwhPer100km().setScale(2, java.math.RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("22.00"), item1.peerMetrics().avgConsumptionKwhPer100km().setScale(2, java.math.RoundingMode.HALF_UP));
        assertEquals(1, item1.peerMetrics().uniqueCars());

        // Item 2: Other spec (2025 RWD)
        PeerModelComparisonItem item2 = response.modelComparisons().stream()
                .filter(i -> i.vehicleSpecificationId().equals(otherSpec.getId()))
                .findFirst()
                .orElseThrow();
        assertFalse(item2.isUserVehicleSpec());
        assertNull(item2.userMetrics());
        assertEquals(new BigDecimal("18.50"), item2.peerMetrics().avgConsumptionKwhPer100km().setScale(2, java.math.RoundingMode.HALF_UP));
        assertEquals(2, item2.peerMetrics().uniqueCars());
    }

    @Test
    void getPeerModelComparison_excludesSelfFromPeers() {
        // User hat 1 spec, aber 2 Autos damit
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "2024 LR", new BigDecimal("75.0"));
        User user = createUser("user@example.com", "DE");
        Car car1 = createCar(user.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        Car car2 = createCar(user.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(car1.getId(), 20.0, 100.0, 6.0);
        addLog(car2.getId(), 21.0, 100.0, 6.3);

        // 1 Peer mit gleicher spec
        User peerUser = createUser("peer@example.com", "DE");
        Car peerCar = createCar(peerUser.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(peerCar.getId(), 22.0, 100.0, 6.16);

        // Act
        PeerModelComparisonResponse response = evLogStatisticsService.getPeerModelComparison(car1, user);

        // Assert: User's 2 Autos sollten NICHT als Peers zählen
        PeerModelComparisonItem item = response.modelComparisons().get(0);
        assertEquals(1, item.peerMetrics().uniqueCars());
        assertEquals(new BigDecimal("22.00"), item.peerMetrics().avgConsumptionKwhPer100km().setScale(2, java.math.RoundingMode.HALF_UP));
    }
}
