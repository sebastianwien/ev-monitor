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
class EvLogStatisticsPeerBenchmarkTest extends AbstractServiceTest {

    @Autowired
    private EvLogStatisticsService evLogStatisticsService;

    // --- helpers ---

    private VehicleSpecification saveSpec(String brand, String model, String variantName) {
        VehicleSpecification spec = VehicleSpecification.createNew(
                brand, model, new BigDecimal("75.0"), null,
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

    private void addLog(UUID carId, double kwh, double odometer) {
        EvLog log = EvLog.createNew(carId, new BigDecimal(kwh), null,
                60, null, (int) odometer, null, null,
                LocalDateTime.now().minusDays(1), null, null, null, false, null);
        evLogRepository.save(log);
    }

    private void addLogWithCost(UUID carId, String kwh, String costEur, int odometer) {
        EvLog log = EvLog.createNew(carId, new BigDecimal(kwh), new BigDecimal(costEur),
                60, null, odometer, null, null,
                LocalDateTime.now().minusDays(1), null, null, null, false, null);
        evLogRepository.save(log);
    }

    private void addLogWithCostAt(UUID carId, String kwh, String costEur, int odometer, LocalDateTime loggedAt) {
        EvLog log = EvLog.createNew(carId, new BigDecimal(kwh), new BigDecimal(costEur),
                60, null, odometer, null, null,
                loggedAt, null, null, null, false, null);
        evLogRepository.save(log);
    }

    // --- tests ---

    @Test
    void peerBenchmark_sameSpec_returnsSpecMatch() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "peer-test-spec-match");

        User owner = createAndSaveUser("owner-spec@example.com");
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(ownerCar.getId(), 22.0, 200);

        User peer = createAndSaveUser("peer-spec@example.com");
        Car peerCar = createCar(peer.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(peerCar.getId(), 18.0, 200);

        EvLogStatisticsResponse result = evLogStatisticsService.getStatistics(ownerCar.getId(), owner.getId(), null, null, null);

        assertNotNull(result.peerBenchmark());
        assertEquals(EvLogStatisticsResponse.PeerBenchmark.MatchType.SPEC, result.peerBenchmark().matchType());
        assertEquals(1, result.peerBenchmark().uniquePeerUsers());
    }

    @Test
    void peerBenchmark_noSpecMatch_fallsBackToModel() {
        VehicleSpecification specA = saveSpec("Tesla", "Model 3", "peer-test-model-a");
        VehicleSpecification specB = saveSpec("Tesla", "Model 3", "peer-test-model-b");

        User owner = createAndSaveUser("owner-model@example.com");
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_3, specA.getId());
        addLog(ownerCar.getId(), 22.0, 200);

        User peer = createAndSaveUser("peer-model@example.com");
        Car peerCar = createCar(peer.getId(), CarBrand.CarModel.MODEL_3, specB.getId());
        addLog(peerCar.getId(), 18.0, 200);

        EvLogStatisticsResponse result = evLogStatisticsService.getStatistics(ownerCar.getId(), owner.getId(), null, null, null);

        assertNotNull(result.peerBenchmark());
        assertEquals(EvLogStatisticsResponse.PeerBenchmark.MatchType.MODEL, result.peerBenchmark().matchType());
        assertEquals(1, result.peerBenchmark().uniquePeerUsers());
    }

    @Test
    void peerBenchmark_noPeersAtAll_returnsNull() {
        // MODEL_Y with a unique variant not used anywhere else - ensures no cross-test pollution
        VehicleSpecification spec = saveSpec("Tesla", "MODEL_Y", "peer-test-no-peers-isolated");

        User owner = createAndSaveUser("owner-alone-" + System.currentTimeMillis() + "@example.com");
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_Y, spec.getId());
        addLog(ownerCar.getId(), 22.0, 200);

        EvLogStatisticsResponse result = evLogStatisticsService.getStatistics(ownerCar.getId(), owner.getId(), null, null, null);

        assertNull(result.peerBenchmark());
    }

    @Test
    void peerBenchmark_seedUserExcluded_stillReturnsIfRealPeerExists() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "peer-test-seed-excl");

        User owner = createAndSaveUser("owner-seed@example.com");
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(ownerCar.getId(), 22.0, 200);

        // seed peer - should be excluded
        User seedUser = userRepository.save(
                createAndSaveUser("seed@example.com").toBuilder().seedData(true).build());
        Car seedCar = createCar(seedUser.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(seedCar.getId(), 15.0, 200);

        // real peer - should be included
        User realPeer = createAndSaveUser("real-seed-peer@example.com");
        Car realCar = createCar(realPeer.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(realCar.getId(), 19.0, 200);

        EvLogStatisticsResponse result = evLogStatisticsService.getStatistics(ownerCar.getId(), owner.getId(), null, null, null);

        assertNotNull(result.peerBenchmark());
        assertEquals(1, result.peerBenchmark().uniquePeerUsers());
    }

    @Test
    void peerBenchmark_costComparison_includesPeersRegardlessOfCountry() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "peer-test-cost");

        User owner = userRepository.save(
                createAndSaveUser("owner-cost@example.com").toBuilder().country("DE").build());
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(ownerCar.getId(), 22.0, 200);

        // Peer without any country set - must still count towards the community avg
        User peerNoCountry = userRepository.save(
                createAndSaveUser("peer-cost-nocountry@example.com").toBuilder().country(null).build());
        Car peerCar = createCar(peerNoCountry.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLogWithCost(peerCar.getId(), "22.0", "7.04", 200);

        EvLogStatisticsResponse result = evLogStatisticsService.getStatistics(ownerCar.getId(), owner.getId(), null, null, null);

        assertNotNull(result.peerBenchmark());
        assertNotNull(result.peerBenchmark().peerAvgCostPerKwh(),
                "peer without country must contribute to the cost average");
    }

    @Test
    void peerBenchmark_peerLogCount_countsOnlyLogsIncludedInStatistics() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "peer-test-logcount");

        User owner = createAndSaveUser("owner-logcount@example.com");
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(ownerCar.getId(), 22.0, 200);

        User peerA = createAndSaveUser("peer-logcount-a@example.com");
        Car peerCarA = createCar(peerA.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(peerCarA.getId(), 18.0, 200);
        addLog(peerCarA.getId(), 19.0, 400);

        // Peer without any logs - counts as driver, contributes no charges
        User peerB = createAndSaveUser("peer-logcount-b@example.com");
        createCar(peerB.getId(), CarBrand.CarModel.MODEL_3, spec.getId());

        EvLogStatisticsResponse.PeerBenchmark benchmark = evLogStatisticsService
                .getStatistics(ownerCar.getId(), owner.getId(), null, null, null)
                .peerBenchmark();

        assertNotNull(benchmark);
        assertEquals(2, benchmark.uniquePeerUsers(), "both peers count as drivers");
        assertEquals(2, benchmark.peerLogCount(), "only peer A contributed charges");
    }

    @Test
    void peerBenchmark_noSufficientDataField_notPresent() {
        // sufficientData field was removed - verify PeerBenchmark record compiles without it
        VehicleSpecification spec = saveSpec("Tesla", "Model S", "peer-test-no-sd");

        User owner = createAndSaveUser("owner-nosd@example.com");
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_S, spec.getId());
        addLog(ownerCar.getId(), 25.0, 300);

        User peer = createAndSaveUser("peer-nosd@example.com");
        Car peerCar = createCar(peer.getId(), CarBrand.CarModel.MODEL_S, spec.getId());
        addLog(peerCar.getId(), 21.0, 300);

        EvLogStatisticsResponse.PeerBenchmark benchmark = evLogStatisticsService
                .getStatistics(ownerCar.getId(), owner.getId(), null, null, null)
                .peerBenchmark();

        assertNotNull(benchmark);
        // MatchType must be present - no sufficientData field anymore
        assertNotNull(benchmark.matchType());
    }

    /** DSGVO: anonymisierte Autos (userId NULL) sind vollwertige Peers und dürfen keine NPE auslösen. */
    @Test
    void peerBenchmark_anonymizedCar_countsAsPeer() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "peer-test-anonymized");

        User owner = createAndSaveUser("owner-anon@example.com");
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(ownerCar.getId(), 22.0, 200);

        User gone = createAndSaveUser("gone-anon@example.com");
        Car anonCar = createCar(gone.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLog(anonCar.getId(), 18.0, 200);
        carRepository.save(anonCar.anonymize());

        EvLogStatisticsResponse result = evLogStatisticsService.getStatistics(ownerCar.getId(), owner.getId(), null, null, null);

        assertNotNull(result.peerBenchmark());
        assertEquals(EvLogStatisticsResponse.PeerBenchmark.MatchType.SPEC, result.peerBenchmark().matchType());
        assertEquals(1, result.peerBenchmark().uniquePeerUsers());
    }

    @Test
    void peerBenchmark_userCostPerKwh_respectsSelectedPeriod() {
        VehicleSpecification spec = saveSpec("Tesla", "Model 3", "peer-test-period-cost");

        User owner = createAndSaveUser("owner-period@example.com");
        Car ownerCar = createCar(owner.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        // Alte Ladung: 40 ct/kWh, liegt ausserhalb des Zeitraums
        addLogWithCostAt(ownerCar.getId(), "10.0", "4.00", 100, LocalDateTime.now().minusDays(40));
        // Aktuelle Ladung: 29 ct/kWh, liegt im Zeitraum
        addLogWithCostAt(ownerCar.getId(), "10.0", "2.90", 200, LocalDateTime.now().minusDays(1));

        User peer = createAndSaveUser("peer-period@example.com");
        Car peerCar = createCar(peer.getId(), CarBrand.CarModel.MODEL_3, spec.getId());
        addLogWithCostAt(peerCar.getId(), "10.0", "3.50", 100, LocalDateTime.now().minusDays(40));
        addLogWithCostAt(peerCar.getId(), "10.0", "3.50", 200, LocalDateTime.now().minusDays(1));

        java.time.LocalDate today = java.time.LocalDate.now();
        EvLogStatisticsResponse period = evLogStatisticsService.getStatistics(
                ownerCar.getId(), owner.getId(), today.minusDays(5), today, null);

        assertNotNull(period.peerBenchmark());
        assertEquals(0, new BigDecimal("0.2900").compareTo(period.peerBenchmark().userPeriodCostPerKwh()),
                "user cost must only include logs within the selected period");
        // Peer-Seite bleibt Lifetime als stabile Referenz
        assertEquals(0, new BigDecimal("0.3500").compareTo(period.peerBenchmark().peerAvgCostPerKwh()));

        EvLogStatisticsResponse lifetime = evLogStatisticsService.getStatistics(
                ownerCar.getId(), owner.getId(), null, null, null);
        assertEquals(0, new BigDecimal("0.3450").compareTo(lifetime.peerBenchmark().userPeriodCostPerKwh()),
                "without a period the user cost stays lifetime");
    }
}
