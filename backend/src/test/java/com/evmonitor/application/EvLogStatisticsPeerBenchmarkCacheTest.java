package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Die Peer-Seite des Vergleichs (fremde Autos derselben Spec) ist 24 h gecacht, die eigene Seite
 * bleibt live. Eigene Logs können den Cache nicht betreffen, weil die Peer-Seite die eigenen Autos
 * ausschließt.
 */
@Transactional
class EvLogStatisticsPeerBenchmarkCacheTest extends AbstractServiceTest {

    @Autowired
    private EvLogStatisticsService evLogStatisticsService;

    private VehicleSpecification saveSpec(String variantName) {
        return vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                "Tesla", "Model 3", new BigDecimal("75.0"), null,
                new BigDecimal("490"), new BigDecimal("15.4"),
                VehicleSpecification.WltpType.COMBINED,
                VehicleSpecification.RatingSource.WLTP, variantName));
    }

    private Car createCar(UUID userId, UUID specId) {
        return carRepository.save(Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2022, "TST-1",
                "Standard", new BigDecimal("75.0"), new BigDecimal("490"), null)
                .toBuilder().vehicleSpecificationId(specId).build());
    }

    private void addLogWithCost(UUID carId, String kwh, String costEur, int odometer, int daysAgo) {
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal(kwh), new BigDecimal(costEur),
                60, null, odometer, null, null,
                LocalDateTime.now().minusDays(daysAgo), null, null, null, false, null));
    }

    private Fixture fixture(String name) {
        VehicleSpecification spec = saveSpec("peer-cache-" + name + "-" + UUID.randomUUID());
        User owner = createAndSaveUser("owner-cache-" + UUID.randomUUID() + "@example.com");
        Car ownerCar = createCar(owner.getId(), spec.getId());
        addLogWithCost(ownerCar.getId(), "10.0", "3.00", 100, 2);
        User peer = createAndSaveUser("peer-cache-" + UUID.randomUUID() + "@example.com");
        Car peerCar = createCar(peer.getId(), spec.getId());
        addLogWithCost(peerCar.getId(), "10.0", "4.00", 100, 2);
        return new Fixture(owner, ownerCar, peerCar);
    }

    private record Fixture(User owner, Car ownerCar, Car peerCar) {}

    @Test
    void peerSideIsServedFromCacheWhilePeersChange() {
        Fixture f = fixture("cached");
        EvLogStatisticsResponse first = evLogStatisticsService.getStatistics(f.ownerCar().getId(), f.owner().getId(), null, null, null);
        assertEquals(0, new BigDecimal("0.4000").compareTo(first.peerBenchmark().peerAvgCostPerKwh()));

        // Neuer, teurer Peer-Log: der gecachte Peer-Wert bleibt bis zum Ablauf stehen
        addLogWithCost(f.peerCar().getId(), "10.0", "8.00", 200, 1);
        EvLogStatisticsResponse second = evLogStatisticsService.getStatistics(f.ownerCar().getId(), f.owner().getId(), null, null, null);

        assertEquals(0, new BigDecimal("0.4000").compareTo(second.peerBenchmark().peerAvgCostPerKwh()));
        assertEquals(first.peerBenchmark().peerLogCount(), second.peerBenchmark().peerLogCount());
    }

    @Test
    void ownSideStaysLiveDespiteCachedPeers() {
        Fixture f = fixture("live");
        EvLogStatisticsResponse first = evLogStatisticsService.getStatistics(f.ownerCar().getId(), f.owner().getId(), null, null, null);
        assertEquals(0, new BigDecimal("0.3000").compareTo(first.peerBenchmark().userPeriodCostPerKwh()));

        addLogWithCost(f.ownerCar().getId(), "10.0", "5.00", 200, 1);
        EvLogStatisticsResponse second = evLogStatisticsService.getStatistics(f.ownerCar().getId(), f.owner().getId(), null, null, null);

        assertEquals(0, new BigDecimal("0.4000").compareTo(second.peerBenchmark().userPeriodCostPerKwh()),
                "own cost must include the new own log immediately");
    }

    @Test
    void differentPeriodIsComputedSeparately() {
        Fixture f = fixture("period");
        evLogStatisticsService.getStatistics(f.ownerCar().getId(), f.owner().getId(), null, null, null);

        addLogWithCost(f.peerCar().getId(), "10.0", "8.00", 200, 1);
        LocalDate today = LocalDate.now();
        EvLogStatisticsResponse period = evLogStatisticsService.getStatistics(
                f.ownerCar().getId(), f.owner().getId(), today.minusDays(5), today, null);

        assertEquals(2, period.peerBenchmark().peerLogCount(),
                "a new period key must see the new peer log");
    }
}
