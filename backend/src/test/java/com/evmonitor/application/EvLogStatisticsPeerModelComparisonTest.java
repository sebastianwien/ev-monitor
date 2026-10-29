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

    /** Anker plus n Winterladungen im Dezember des Vorjahres, je 100 km mit kwhPer100 kWh. */
    private void addWinterLogs(UUID carId, int n, double kwhPer100) {
        LocalDateTime start = LocalDateTime.of(LocalDateTime.now().getYear() - 1, 12, 1, 18, 0);
        EvLog anchor = EvLog.createNew(carId, new BigDecimal("0.1"), null,
                1, null, 0, null, null, start, null, null, null, false, null);
        evLogRepository.save(anchor.toBuilder().includeInStatistics(true).build());
        for (int i = 1; i <= n; i++) {
            EvLog log = EvLog.createNew(carId, BigDecimal.valueOf(kwhPer100), new BigDecimal("6.0"),
                    60, null, i * 100, null, null, start.plusDays(i), null, null, null, false, null);
            evLogRepository.save(log.toBuilder().includeInStatistics(true).build());
        }
    }

    // --- tests ---

    private Car peerCar(int i, VehicleSpecification spec) {
        return createCar(createUser("peer" + i + "@example.com", "DE").getId(), CarBrand.CarModel.MODEL_3, spec.getId());
    }

    @Test
    void getPeerModelComparison_allVariantsWinter_fromPeerWinterLogsOfAllVariants() {
        VehicleSpecification userSpec = saveSpec("Tesla", "Model 3", "2024 LR", new BigDecimal("75.0"));
        VehicleSpecification otherSpec = saveSpec("Tesla", "Model 3", "2025 RWD", new BigDecimal("60.0"));
        User user = createUser("user@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, userSpec.getId());
        addWinterLogs(userCar.getId(), 10, 40.0);

        // 5 Fahrer, 10 Winterladungen: 6 x 22 und 4 x 27 kWh/100km
        for (int i = 1; i <= 3; i++) addWinterLogs(peerCar(i, userSpec).getId(), 2, 22.0);
        for (int i = 4; i <= 5; i++) addWinterLogs(peerCar(i, otherSpec).getId(), 2, 27.0);

        PeerModelComparisonResponse response = evLogStatisticsService.getPeerModelComparison(userCar, user);

        // eigene 40 kWh/100km zählen nicht
        assertEquals(new BigDecimal("24.00"),
                response.allVariantsWinterConsumptionKwhPer100km().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void getPeerModelComparison_allVariantsWinter_nullBelowFiveWinterDrivers() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "2024 LR", new BigDecimal("75.0"));
        User user = createUser("user@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, spec.getId());

        for (int i = 1; i <= 4; i++) addWinterLogs(peerCar(i, spec).getId(), 3, 22.0);
        // fünfter Fahrer nur mit Sommerladung
        addLog(peerCar(5, spec).getId(), 20.0, 100.0, 6.0);

        assertNull(evLogStatisticsService.getPeerModelComparison(userCar, user).allVariantsWinterConsumptionKwhPer100km());
    }

    @Test
    void getPeerModelComparison_allVariantsWinter_nullBelowTenWinterLogs() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "2024 LR", new BigDecimal("75.0"));
        User user = createUser("user@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, spec.getId());

        for (int i = 1; i <= 4; i++) addWinterLogs(peerCar(i, spec).getId(), 2, 22.0);
        addWinterLogs(peerCar(5, spec).getId(), 1, 22.0);

        assertNull(evLogStatisticsService.getPeerModelComparison(userCar, user).allVariantsWinterConsumptionKwhPer100km());
    }

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

    @Test
    void getPeerModelComparison_allVariants_poolsCarsOfAllSpecsOfTheModel() {
        VehicleSpecification userSpec = saveSpec("Tesla", "Model 3", "2024 LR", new BigDecimal("75.0"));
        VehicleSpecification otherSpec = saveSpec("Tesla", "Model 3", "2025 RWD", new BigDecimal("60.0"));
        User user = createUser("user@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, userSpec.getId());
        addLog(userCar.getId(), 30.0, 100.0, 9.0);

        // 22 kWh/100km zu 0,28 €/kWh in der eigenen Variante, 18 kWh/100km zu 0,20 €/kWh in der anderen
        Car samePeer = createCar(createUser("p1@example.com", "DE").getId(), CarBrand.CarModel.MODEL_3, userSpec.getId());
        addLog(samePeer.getId(), 22.0, 100.0, 6.16);
        Car otherPeer = createCar(createUser("p2@example.com", "DE").getId(), CarBrand.CarModel.MODEL_3, otherSpec.getId());
        addLog(otherPeer.getId(), 18.0, 100.0, 3.60);

        PeerModelComparisonResponse response = evLogStatisticsService.getPeerModelComparison(userCar, user);

        PeerMetrics all = response.allVariants();
        assertNotNull(all);
        assertEquals(2, all.uniqueCars());
        assertEquals(new BigDecimal("20.00"), all.avgConsumptionKwhPer100km().setScale(2, java.math.RoundingMode.HALF_UP));
        // 9,76 € für 40 kWh
        assertEquals(new BigDecimal("0.24"), all.avgCostPerKwh().setScale(2, java.math.RoundingMode.HALF_UP));
        // Vorhandene Felder bleiben je Variante
        assertEquals(2, response.modelComparisons().size());
    }

    @Test
    void getPeerModelComparison_allVariants_excludesUsersOwnCarsOfEveryVariant() {
        VehicleSpecification userSpec = saveSpec("Tesla", "Model 3", "2024 LR", new BigDecimal("75.0"));
        VehicleSpecification otherSpec = saveSpec("Tesla", "Model 3", "2025 RWD", new BigDecimal("60.0"));
        User user = createUser("user@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, userSpec.getId());
        Car userSecondCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, otherSpec.getId());
        addLog(userCar.getId(), 30.0, 100.0, 9.0);
        addLog(userSecondCar.getId(), 30.0, 100.0, 9.0);

        Car peer = createCar(createUser("p1@example.com", "DE").getId(), CarBrand.CarModel.MODEL_3, otherSpec.getId());
        addLog(peer.getId(), 18.0, 100.0, 3.60);

        PeerMetrics all = evLogStatisticsService.getPeerModelComparison(userCar, user).allVariants();

        assertEquals(1, all.uniqueCars());
        assertEquals(new BigDecimal("18.00"), all.avgConsumptionKwhPer100km().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void getPeerModelComparison_withoutSpec_emptyAsBefore() {
        User user = createUser("user@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, null);

        PeerModelComparisonResponse response = evLogStatisticsService.getPeerModelComparison(userCar, user);

        assertTrue(response.modelComparisons().isEmpty());
        assertNull(response.allVariants());
    }

    /** Die fremde Seite liest alle Ladungen aller Varianten und ist 24 h gecacht, die eigene Seite bleibt live. */
    @Test
    void getPeerModelComparison_peerSideCachedWhilePeersChange_ownSideLive() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "cache-" + UUID.randomUUID(), new BigDecimal("75.0"));
        User user = createUser("cache-user-" + UUID.randomUUID() + "@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(userCar.getId(), 15.0, 100, 3.0);
        Car peer = createCar(createUser("cache-peer-" + UUID.randomUUID() + "@example.com", "DE").getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(peer.getId(), 15.0, 100, 4.5);

        PeerModelComparisonItem first = itemOf(evLogStatisticsService.getPeerModelComparison(userCar, user), spec);

        addLog(peer.getId(), 15.0, 100, 9.0);
        addLog(userCar.getId(), 15.0, 100, 9.0);
        PeerModelComparisonResponse second = evLogStatisticsService.getPeerModelComparison(userCar, user);
        PeerModelComparisonItem again = itemOf(second, spec);

        assertEquals(0, first.peerMetrics().avgCostPerKwh().compareTo(again.peerMetrics().avgCostPerKwh()));
        assertEquals(0, first.peerMetrics().avgCostPerKwh().compareTo(second.allVariants().avgCostPerKwh()));
        assertTrue(again.userMetrics().costPerKwh().compareTo(first.userMetrics().costPerKwh()) > 0,
                "own cost must include the new own log immediately");
    }

    /** Eine nach dem Cachen angelegte Variante, der der Nutzer sein Auto zuordnet, erscheint sofort mit eigenen Werten. */
    @Test
    void getPeerModelComparison_newSpecAfterCaching_userRowStillPresent() {
        VehicleSpecification oldSpec = saveSpec("Tesla", "Model 3", "cache-old-" + UUID.randomUUID(), new BigDecimal("60.0"));
        User user = createUser("cache-spec-" + UUID.randomUUID() + "@example.com", "DE");
        Car userCar = createCar(user.getId(), CarBrand.CarModel.MODEL_3, oldSpec.getId());
        addLog(userCar.getId(), 15.0, 100, 3.0);
        evLogStatisticsService.getPeerModelComparison(userCar, user);

        VehicleSpecification newSpec = saveSpec("Tesla", "Model 3", "cache-new-" + UUID.randomUUID(), new BigDecimal("75.0"));
        Car moved = carRepository.save(userCar.toBuilder().vehicleSpecificationId(newSpec.getId()).build());

        PeerModelComparisonItem own = itemOf(evLogStatisticsService.getPeerModelComparison(moved, user), newSpec);
        assertTrue(own.isUserVehicleSpec());
        assertNotNull(own.userMetrics());
    }

    private static PeerModelComparisonItem itemOf(PeerModelComparisonResponse response, VehicleSpecification spec) {
        return response.modelComparisons().stream()
                .filter(i -> i.vehicleSpecificationId().equals(spec.getId()))
                .findFirst().orElseThrow();
    }
}
