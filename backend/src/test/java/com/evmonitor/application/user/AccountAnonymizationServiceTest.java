package com.evmonitor.application.user;

import com.evmonitor.testutil.AbstractServiceTest;
import com.evmonitor.application.EvLogStatisticsService;
import com.evmonitor.application.PublicModelService;
import com.evmonitor.application.PublicModelStatsResponse;
import com.evmonitor.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DSGVO-Kontolöschung nach Plan A: Auto, Logs und Trips bleiben anonymisiert erhalten,
 * jeder Personenbezug (Besitzer, Kennzeichen, Bild, Orte, Freitexte, exakte Zeitpunkte) fällt.
 */
@Transactional
class AccountAnonymizationServiceTest extends AbstractServiceTest {

    @Autowired AccountAnonymizationService service;
    @Autowired EvTripRepository evTripRepository;
    @Autowired PublicModelService publicModelService;
    @Autowired EvLogStatisticsService statisticsService;
    @Autowired org.springframework.cache.CacheManager cacheManager;

    @Test
    void anonymize_cutsPersonalDataKeepsStatisticsAndOrder() {
        User gone = createAndSaveUser("gone@example.com");
        Car car = carRepository.save(Car.createNew(gone.getId(), CarBrand.CarModel.MODEL_3, 2022, "B-EV 1",
                "LR", new BigDecimal("75"), new BigDecimal("300"), null));
        LocalDateTime day = LocalDateTime.of(2026, 3, 10, 0, 0);
        EvLog l1 = save(car.getId(), day.plusHours(7), 20_000, "u33dc0");
        EvLog l2 = save(car.getId(), day.plusHours(18), 20_300, "u33dc0");
        EvLog l3 = save(car.getId(), day.plusDays(1).plusHours(9), 20_600, "u33dc1");
        EvTrip trip = evTripRepository.save(EvTrip.builder().userId(gone.getId()).carId(car.getId())
                .dataSource("TESLA_LIVE").status("COMPLETED")
                .tripStartedAt(OffsetDateTime.of(2026, 3, 10, 8, 15, 0, 0, ZoneOffset.UTC))
                .tripEndedAt(OffsetDateTime.of(2026, 3, 10, 9, 0, 0, 0, ZoneOffset.UTC))
                .distanceKm(new BigDecimal("42.0")).locationStartGeohash("u33dc0abc").locationEndGeohash("u33dc1xyz")
                .routePolyline("abc").tracePolyline("def").rawPayload("{}").feedback("bei Oma").build());
        EvTrip deletedTrip = evTripRepository.save(EvTrip.builder().userId(gone.getId()).carId(car.getId())
                .dataSource("TESLA_LIVE").status("COMPLETED").tripStartedAt(OffsetDateTime.now())
                .deletedAt(OffsetDateTime.now()).build());

        List<UUID> carIds = service.anonymizeCarsOf(gone.getId());

        assertEquals(List.of(car.getId()), carIds);
        Car anon = carRepository.findById(car.getId()).orElseThrow();
        assertTrue(anon.isAnonymized());
        assertNull(anon.getUserId());
        assertNull(anon.getLicensePlate());
        assertEquals(2022, anon.getYear());
        assertTrue(carRepository.findAllByUserId(gone.getId()).isEmpty());

        List<EvLog> logs = evLogRepository.findAllByCarIds(carIds).stream()
                .sorted(java.util.Comparator.comparing(EvLog::getLoggedAt)).toList();
        assertEquals(List.of(l1.getId(), l2.getId(), l3.getId()), logs.stream().map(EvLog::getId).toList(),
                "Reihenfolge innerhalb des Tages bleibt erhalten");
        assertEquals(day, logs.get(0).getLoggedAt());
        assertEquals(day.plusMinutes(1), logs.get(1).getLoggedAt());
        assertEquals(day.plusDays(1), logs.get(2).getLoggedAt());
        logs.forEach(l -> {
            assertNull(l.getGeohash());
            assertNull(l.getRawImportData());
            assertNull(l.getChargingProviderId());
            assertNotNull(l.getOdometerKm());
            assertNotNull(l.getKwhCharged());
        });

        EvTrip t = evTripRepository.findById(trip.getId()).orElseThrow();
        assertNull(t.getUserId());
        assertNull(t.getLocationStartGeohash());
        assertNull(t.getLocationEndGeohash());
        assertNull(t.getRoutePolyline());
        assertNull(t.getTracePolyline());
        assertNull(t.getRawPayload());
        assertNull(t.getFeedback());
        assertEquals(new BigDecimal("42.0"), t.getDistanceKm());
        assertEquals(OffsetDateTime.of(2026, 3, 10, 0, 0, 0, 0, ZoneOffset.UTC), t.getTripStartedAt());
        assertEquals(45, java.time.Duration.between(t.getTripStartedAt(), t.getTripEndedAt()).toMinutes(),
                "Dauer bleibt erhalten");
        assertTrue(evTripRepository.findById(deletedTrip.getId()).isEmpty(), "soft-gelöschte Trips werden hart gelöscht");
    }

    @Test
    void anonymize_alsoCoversSoftDeletedCars() {
        // DSGVO: ein Auto im Papierkorb darf den Personenbezug nicht ueberleben,
        // wenn der User sein Konto loescht. Der Purge raeumt es spaeter ohnehin hart ab.
        User gone = createAndSaveUser("trashcan@example.com");
        Car car = carRepository.save(Car.createNew(gone.getId(), CarBrand.CarModel.MODEL_3, 2022, "B-EV 9",
                "LR", new BigDecimal("75"), new BigDecimal("300"), null));
        carRepository.save(car.softDelete());

        List<UUID> carIds = service.anonymizeCarsOf(gone.getId());

        assertEquals(List.of(car.getId()), carIds, "Soft-geloeschtes Auto muss mit anonymisiert werden");
        Car anon = carRepository.findByIdIncludingDeleted(car.getId()).orElseThrow();
        assertTrue(anon.isAnonymized());
        assertNull(anon.getUserId());
        assertNull(anon.getLicensePlate());
        assertTrue(anon.isDeleted(), "Der Soft-Delete bleibt bestehen, der Purge raeumt spaeter ab");
    }

    @Test
    void anonymizedCar_stillFeedsPublicModelStatsAndPeerBenchmarkWithoutErrors() {
        VehicleSpecification spec = vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                "Tesla", "Model 3", new BigDecimal("75.0"), null, new BigDecimal("490"), new BigDecimal("15.4"),
                VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.WLTP, "anon-readpath"));
        User gone = createAndSaveUser("gone2@example.com");
        Car goneCar = carRepository.save(Car.createNew(gone.getId(), CarBrand.CarModel.MODEL_3, 2022, null,
                null, new BigDecimal("75"), null, null).toBuilder().vehicleSpecificationId(spec.getId()).build());
        save(goneCar.getId(), LocalDateTime.now().minusDays(3), 1_000, null);
        save(goneCar.getId(), LocalDateTime.now().minusDays(2), 1_300, null);
        User stays = createAndSaveUser("stays@example.com");
        Car staysCar = carRepository.save(Car.createNew(stays.getId(), CarBrand.CarModel.MODEL_3, 2023, null,
                null, new BigDecimal("75"), null, null).toBuilder().vehicleSpecificationId(spec.getId()).build());
        save(staysCar.getId(), LocalDateTime.now().minusDays(3), 5_000, null);
        save(staysCar.getId(), LocalDateTime.now().minusDays(2), 5_300, null);

        service.anonymizeCarsOf(gone.getId());
        cacheManager.getCache("modelStats").clear();

        PublicModelStatsResponse stats = publicModelService.getModelStats("Tesla", "Model 3", false).orElseThrow();
        assertTrue(stats.uniqueCars() >= 2, "anonymisiertes Auto zählt weiter in der Community-Statistik");
        assertTrue(stats.uniqueContributors() >= 2, "anonymisiertes Auto zählt als eigener Beitragender");
        var peer = statisticsService.getStatistics(staysCar.getId(), stays.getId(), null, null, null).peerBenchmark();
        assertNotNull(peer);
        assertTrue(peer.uniquePeerUsers() >= 1);
    }

    private EvLog save(UUID carId, LocalDateTime at, int odo, String geohash) {
        return evLogRepository.save(EvLog.createNew(carId, new BigDecimal("30"), new BigDecimal("12"), 60, geohash,
                odo, null, null, at, null, null, null, false, null));
    }
}
