package com.evmonitor.application;

import com.evmonitor.application.range.ModelRangeConsumption;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reichweite auf der Modellseite: Fahrten anderer Fahrer ab 2 Fahrern (Stufe 2), sonst die Schätzung aus
 * Ladungen und WLTP (Stufe 4); km je Variante aus RangeMath. Jeder Test nutzt ein eigenes Modell, die Daten
 * bleiben zwischen den Tests stehen (AbstractIntegrationTest ist nicht transaktional).
 */
class PublicModelServiceRangeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublicModelService publicModelService;
    @Autowired
    private EvTripRepository tripRepository;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        for (String name : List.of("modelStats", "topModels", "communityModel", "fleetFactors", DriveConsumptionService.MODEL_CACHE)) {
            cacheManager.getCache(name).clear();
        }
    }

    private User user() {
        return createAndSaveUser("pmrange-" + UUID.randomUUID() + "@example.com");
    }

    private Car car(User owner, CarBrand.CarModel model, BigDecimal batteryKwh) {
        return carRepository.save(Car.createNew(owner.getId(), model, 2023,
                "R-" + UUID.randomUUID().toString().substring(0, 8), "Test Trim", batteryKwh, new BigDecimal("200.0"), null));
    }

    private void trip(Car car, LocalDate day, double km, double kwh) {
        OffsetDateTime at = day.atTime(10, 0).atOffset(ZoneOffset.UTC);
        tripRepository.save(EvTrip.builder().userId(car.getUserId()).carId(car.getId()).dataSource("API_UPLOAD")
                .tripStartedAt(at).tripEndedAt(at.plusMinutes(60)).distanceKm(BigDecimal.valueOf(km))
                .estimatedConsumedKwh(BigDecimal.valueOf(kwh)).status("COMPLETED").createdAt(OffsetDateTime.now()).build());
    }

    private void logs(Car car, int count, String kwhPer100km) {
        for (int i = 0; i < count; i++) {
            evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal(kwhPer100km), new BigDecimal("10.00"), 30,
                    "u33d1", 40000 + i * 100, new BigDecimal("11.0"), null,
                    LocalDateTime.of(2026, 4, 1, 12, 0).plusDays(i), ChargingType.UNKNOWN, null, null, false, null));
        }
    }

    private void spec(CarBrand.CarModel model, String gross, String net, String wltp) {
        vehicleSpecificationRepository.save(VehicleSpecification.createNew(model.getBrand().name(), model.name(),
                new BigDecimal(gross), new BigDecimal(net), new BigDecimal("400.0"), new BigDecimal(wltp),
                VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.WLTP));
    }

    private PublicModelStatsResponse stats(CarBrand.CarModel model) {
        return publicModelService.getModelStats(model.getBrand().getDisplayString(), model.getDisplayName(), false).orElseThrow();
    }

    @Test
    void mitFahrtenZweierFahrer_reichweiteAusDerenFahrverbrauch_kmJeVariante() {
        CarBrand.CarModel model = CarBrand.CarModel.IX3;
        spec(model, "80.0", "74.0", "18.5");
        for (int i = 0; i < 2; i++) {
            Car c = car(user(), model, new BigDecimal("80.0"));
            logs(c, 2, "24.0"); // Ladungen mit Stand, zählen für die Reichweite nicht
            trip(c, LocalDate.of(2026, 10, 1 + i), 150, 24.0); // 16 kWh/100 km
            trip(c, LocalDate.of(2026, 1, 1 + i), 150, 30.0); // Winter 20
        }

        PublicModelStatsResponse s = stats(model);
        ModelRangeConsumption range = s.range();

        assertEquals(RangeBasis.Source.PEER_DRIVE, range.overall().source());
        assertEquals(PeerDrive.Scope.MODEL, range.overall().scope());
        assertEquals(0, new BigDecimal("18.00").compareTo(range.overall().consumptionKwhPer100km())); // 54 kWh / 300 km
        assertEquals(RangeBasis.Source.PEER_DRIVE, range.winter().source());
        assertEquals(0, new BigDecimal("20.00").compareTo(range.winter().consumptionKwhPer100km()));
        // 74 × 0,9 × 100 / 18 = 370, Winter / 20 = 333; keine Fahrerspanne, die Basis sind Fahrten
        PublicModelStatsResponse.VariantRange v = s.wltpVariants().get(0).range();
        assertEquals(370, v.rangeKm());
        assertEquals(333, v.winterRangeKm());
        assertNull(v.driverRangeMinKm());
    }

    @Test
    void ohneFahrten_ladungenOhneDeckel_winterOhneLadungenAlsWltpSchaetzung() {
        CarBrand.CarModel model = CarBrand.CarModel.I7;
        spec(model, "105.0", "100.0", "20.0");
        Car c = car(user(), model, new BigDecimal("105.0"));
        logs(c, 4, "26.0"); // 3 Verbrauchswerte je 26, im April (keine Saison)

        PublicModelStatsResponse s = stats(model);
        ModelRangeConsumption range = s.range();

        // Ladungen 26 liegen über WLTP 20 × 1,21: trotzdem die Messung, kein Deckel
        assertEquals(RangeBasis.Source.PEER_CHARGES, range.overall().source());
        assertEquals(0, new BigDecimal("26.0").compareTo(range.overall().consumptionKwhPer100km()));
        assertEquals(RangeBasis.Source.WLTP_ESTIMATE, range.winter().source());
        assertEquals(0, new BigDecimal("29.8").compareTo(range.winter().consumptionKwhPer100km())); // 20 × 1,49, keine Winterladungen
        PublicModelStatsResponse.VariantRange v = s.wltpVariants().get(0).range();
        assertEquals(346, v.rangeKm()); // 100 × 0,9 × 100 / 26 = 346,2
        assertEquals(302, v.winterRangeKm());
        assertEquals(360, v.prospectRangeKm()); // WLTP 400 × 0,9

        // Lade-Fakten der Variante: 100 × 0,7 an der Säule, 100 × 0,6 an der Wallbox, je durch den
        // Wirkungsgrad aus der Konfiguration (Testprofil 1,0); keine Minuten ohne gemessene Schnellladeleistung
        PublicModelStatsResponse.VariantCharging charging = s.wltpVariants().get(0).charging();
        assertEquals(0, new BigDecimal("70").compareTo(charging.fastStopPlugKwh()));
        assertEquals(0, new BigDecimal("60").compareTo(charging.homeChargePlugKwh()));
        assertNull(charging.fastStopMinutes());

        TopModelResponse top = publicModelService.getTopModels(false).stream()
                .filter(r -> r.model().equals(model.name())).findFirst().orElseThrow();
        assertEquals("PEER_CHARGES", top.rangeConsumptionSource());
        assertEquals(0, new BigDecimal("26.0").compareTo(top.rangeConsumptionKwhPer100km()));
        assertEquals(0, new BigDecimal("26.0").compareTo(top.avgConsumptionKwhPer100km())); // Kostenbasis bleiben die Ladungen
    }
}
