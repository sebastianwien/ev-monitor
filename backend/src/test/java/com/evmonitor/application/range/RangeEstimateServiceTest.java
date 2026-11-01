package com.evmonitor.application.range;

import com.evmonitor.application.DriveConsumptionService;
import com.evmonitor.application.DriveConsumptionService.Period;
import com.evmonitor.application.DriveStatistics;
import com.evmonitor.application.range.RangeEstimateService.OwnCharges;
import com.evmonitor.application.range.RangeEstimateService.RangeStatistics;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractServiceTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Die vier Stufen der Reichweite an einem eigenen Auto, je Zeitraum einzeln, km nach RangeMath. */
@Transactional
class RangeEstimateServiceTest extends AbstractServiceTest {

    @Autowired
    private RangeEstimateService service;
    @Autowired
    private DriveConsumptionService driveConsumptionService;
    @Autowired
    private EvTripRepository tripRepository;
    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private static final LocalDate END = LocalDate.of(2026, 10, 28);
    private static final LocalDate START = END.minusDays(27);
    private static final Period PERIOD = new Period(START, END);

    /** Modellstatistik und Rangliste sind JVM-weit gecacht: Stände anderer Testklassen dürfen nicht hereinspielen. */
    @org.junit.jupiter.api.BeforeEach
    void clearModelCaches() {
        for (String name : java.util.List.of("modelStats", "topModels", "communityModel", "fleetFactors", DriveConsumptionService.MODEL_CACHE)) {
            org.springframework.cache.Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }

    private User user() {
        return createAndSaveUser("range-" + UUID.randomUUID() + "@example.com");
    }

    private Car car(User owner, CarBrand.CarModel model, UUID specId) {
        return carRepository.save(TestDataBuilder.createTestCar(owner.getId(), model, new BigDecimal("60.0"))
                .toBuilder().vehicleSpecificationId(specId).build());
    }

    private void kwhTrip(Car car, LocalDate day, double km, double kwh) {
        OffsetDateTime at = day.atTime(10, 0).atOffset(ZoneOffset.UTC);
        tripRepository.save(EvTrip.builder().userId(car.getUserId()).carId(car.getId()).dataSource("API_UPLOAD")
                .tripStartedAt(at).tripEndedAt(at.plusMinutes(60)).distanceKm(BigDecimal.valueOf(km))
                .estimatedConsumedKwh(BigDecimal.valueOf(kwh)).status("COMPLETED").createdAt(OffsetDateTime.now()).build());
    }

    private RangeStatistics range(Car car, User owner, OwnCharges charges) {
        DriveStatistics drive = driveConsumptionService.forStatistics(car, owner, PERIOD);
        return service.forStatistics(car, owner, PERIOD, drive, charges);
    }

    @Test
    void stufe1_eigeneFahrten_mitKmAusDerEigenenKapazitaet() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3, null);
        kwhTrip(c, START.plusDays(3), 150, 30.0); // 20 kWh/100 km

        RangeEstimate current = range(c, u, new OwnCharges(new BigDecimal("49.5"), null, null)).current();

        assertEquals(RangeBasis.Source.OWN_DRIVE, current.source());
        assertEquals(270, current.rangeKm()); // 60 × 0,9 × 100 / 20
    }

    @Test
    void stufe2_fahrtenAndererFahrer_varianteVorModell_ohneBedingungAnDieLadungen() {
        CarBrand.CarModel model = CarBrand.CarModel.IONIQ_5;
        UUID spec = UUID.randomUUID();
        for (int i = 0; i < 2; i++) kwhTrip(car(user(), model, spec), START.plusDays(1 + i), 150, 24.0); // 16
        for (int i = 0; i < 2; i++) kwhTrip(car(user(), model, null), START.plusDays(3 + i), 150, 33.0); // 22
        User u = user();
        Car c = car(u, model, spec);

        RangeStatistics r = range(c, u, new OwnCharges(new BigDecimal("15.0"), null, null)); // Ladungen kleiner, egal

        assertEquals(RangeBasis.Source.PEER_DRIVE, r.current().source());
        assertEquals(PeerDrive.Scope.VARIANT, r.current().scope());
        assertEquals(0, new BigDecimal("16.00").compareTo(r.current().consumptionKwhPer100km()));
        assertEquals(338, r.current().rangeKm()); // 60 × 0,9 × 100 / 16 = 337,5
    }

    @Test
    void stufe3_eigeneLadungen_wennNurEinAndererFahrerDaIst() {
        CarBrand.CarModel model = CarBrand.CarModel.EV_6;
        kwhTrip(car(user(), model, null), START.plusDays(1), 150, 24.0);
        User u = user();
        Car c = car(u, model, null);

        RangeStatistics r = range(c, u, new OwnCharges(new BigDecimal("22.0"), new BigDecimal("18.0"), null));

        assertEquals(RangeBasis.Source.OWN_CHARGES, r.current().source());
        assertEquals(245, r.current().rangeKm()); // 60 × 0,9 × 100 / 22 = 245,45
        assertEquals(RangeBasis.Source.OWN_CHARGES, r.summer().source());
        assertEquals(300, r.summer().rangeKm());
    }

    @Test
    void stufe4_schaetzungAusWltpMalFlottenfaktor_sonstNull() {
        User u = user();
        Car withoutSpec = car(u, CarBrand.CarModel.ENYAQ, null);
        assertNull(range(withoutSpec, u, OwnCharges.NONE).current());

        VehicleSpecification spec = vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                CarBrand.BMW.name(), CarBrand.CarModel.IX1.name(), new BigDecimal("83.9"), new BigDecimal("80.7"),
                new BigDecimal("500.0"), new BigDecimal("16.0"), VehicleSpecification.WltpType.COMBINED,
                VehicleSpecification.RatingSource.WLTP));
        Car withSpec = car(u, CarBrand.CarModel.IX1, spec.getId());

        RangeStatistics r = range(withSpec, u, OwnCharges.NONE);

        // Zu wenige Modelle für einen Flotten-Median: fester Faktor 1,21 → 16 × 1,21 = 19,36; Winter 1,49 → 23,84
        assertEquals(RangeBasis.Source.WLTP_ESTIMATE, r.current().source());
        assertEquals(0, new BigDecimal("19.36").compareTo(r.current().consumptionKwhPer100km()));
        assertEquals(375, r.current().rangeKm()); // Akku aus der Spec (80,7 netto vor dem Custom-Wert): 80,7 × 0,9 × 100 / 19,36 = 375,2
        assertEquals(0, new BigDecimal("23.84").compareTo(r.winter().consumptionKwhPer100km()));
    }

    @Test
    void jederZeitraumEntscheidetFuerSich() {
        CarBrand.CarModel model = CarBrand.CarModel.POLESTAR_2;
        for (int i = 0; i < 2; i++) kwhTrip(car(user(), model, null), LocalDate.of(2025, 12, 1 + i), 150, 36.0); // Winter 24
        User u = user();
        Car c = car(u, model, null);
        kwhTrip(c, LocalDate.of(2026, 7, 3), 150, 27.0); // eigener Sommer 18, nicht im Fenster

        RangeStatistics r = range(c, u, new OwnCharges(new BigDecimal("21.0"), null, null));

        assertEquals(RangeBasis.Source.OWN_CHARGES, r.current().source());
        assertEquals(RangeBasis.Source.OWN_DRIVE, r.summer().source());
        assertEquals(300, r.summer().rangeKm());
        assertEquals(RangeBasis.Source.PEER_DRIVE, r.winter().source());
        assertEquals(PeerDrive.Scope.MODEL, r.winter().scope());
        assertEquals(225, r.winter().rangeKm());
    }
}
