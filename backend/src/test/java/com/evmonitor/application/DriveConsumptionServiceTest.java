package com.evmonitor.application;

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

/**
 * Fahrverbrauch aus {@code ev_trip}: nur zugelassene Quellen, nur abgeschlossene, sichtbare Fahrten ab
 * 20 km. Reichweite nach {@link RangeBasisPolicy}; der Modellschnitt zählt Nutzer, nicht Autos.
 */
@Transactional
class DriveConsumptionServiceTest extends AbstractServiceTest {

    @Autowired
    private DriveConsumptionService service;
    @Autowired
    private EvTripRepository tripRepository;
    @Autowired
    private EvLogStatisticsService statisticsService;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** Fenster je Test eigen, damit der Modell-Cache keine Werte eines anderen Tests liefert. */
    private static final LocalDate END = LocalDate.of(2026, 10, 28);
    private static final LocalDate START = END.minusDays(27);

    private User user() {
        return createAndSaveUser("drive-" + UUID.randomUUID() + "@example.com");
    }

    private Car car(User owner, CarBrand.CarModel model) {
        return carRepository.save(TestDataBuilder.createTestCar(owner.getId(), model, new BigDecimal("60.0")));
    }

    private EvTrip.EvTripBuilder trip(Car car, String source, LocalDate day, double km) {
        OffsetDateTime at = day.atTime(10, 0).atOffset(ZoneOffset.UTC);
        return EvTrip.builder().userId(car.getUserId()).carId(car.getId()).dataSource(source)
                .tripStartedAt(at).tripEndedAt(at.plusMinutes(60)).distanceKm(BigDecimal.valueOf(km))
                .status("COMPLETED").createdAt(OffsetDateTime.now());
    }

    private void kwhTrip(Car car, String source, LocalDate day, double km, double kwh) {
        tripRepository.save(trip(car, source, day, km).estimatedConsumedKwh(BigDecimal.valueOf(kwh)).build());
    }

    private DriveStatistics stats(Car car, User owner, LocalDate start, LocalDate end, String charges) {
        return service.forStatistics(car, owner, new DriveConsumptionService.Period(start, end),
                charges == null ? null : new BigDecimal(charges));
    }

    @Test
    void model3Fall_reichweiteMitFahrverbrauchStattLadungen() {
        // Regression: 49,5 aus Ladungen (mit Stand), gefahren 23,8
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(3), 120, 28.56);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(10), 80, 19.04);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(11), 8, 4.0); // Kurzstrecke zählt nicht

        DriveStatistics s = stats(c, u, START, END, "49.5");

        assertEquals(0, new BigDecimal("23.80").compareTo(s.driveConsumptionKwhPer100km()));
        assertEquals(RangeBasis.Source.OWN_DRIVE, s.rangeBasis().source());
        assertEquals(0, new BigDecimal("23.80").compareTo(s.rangeBasis().consumptionKwhPer100km()));
    }

    @Test
    void smartcarUndTeslaInferred_zaehlenNicht() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.POLESTAR_2);
        kwhTrip(c, "SMARTCAR_LIVE", START.plusDays(2), 150, 27.0);
        kwhTrip(c, "TESLA_INFERRED", START.plusDays(3), 150, 27.0);

        DriveStatistics s = stats(c, u, START, END, "30.0");

        assertNull(s.driveConsumptionKwhPer100km());
        assertEquals(RangeBasis.Source.OWN_CHARGES, s.rangeBasis().source());
    }

    @Test
    void schattenGeloeschtUndFremderHalter_zaehlenNicht() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.POLESTAR_2);
        tripRepository.save(trip(c, "POLESTAR_DATA_PORTAL", START.plusDays(2), 150).estimatedConsumedKwh(new BigDecimal("27")).status("SHADOW").build());
        tripRepository.save(trip(c, "POLESTAR_DATA_PORTAL", START.plusDays(3), 150).estimatedConsumedKwh(new BigDecimal("27")).deletedAt(OffsetDateTime.now()).build());
        tripRepository.save(trip(c, "POLESTAR_DATA_PORTAL", START.plusDays(4), 150).estimatedConsumedKwh(new BigDecimal("27")).userId(UUID.randomUUID()).build());

        assertNull(stats(c, u, START, END, null).driveConsumptionKwhPer100km());
    }

    @Test
    void enyaqFall_unsinnigeFahrten_rueckfallAufLadungen() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.ENYAQ);
        kwhTrip(c, "API_UPLOAD", START.plusDays(2), 200, 2.6); // 1,3 kWh/100 km
        kwhTrip(c, "API_UPLOAD", START.plusDays(5), 300, 3.9);

        DriveStatistics s = stats(c, u, START, END, "21.0");

        assertNull(s.driveConsumptionKwhPer100km());
        assertEquals(RangeBasis.Source.OWN_CHARGES, s.rangeBasis().source());
        assertEquals(0, new BigDecimal("21.0").compareTo(s.rangeBasis().consumptionKwhPer100km()));
    }

    @Test
    void socFahrt_mitEffektiverKapazitaet() {
        User u = user();
        Car c = carRepository.save(TestDataBuilder.createTestCar(u.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("60.0"))
                .toBuilder().batteryDegradationPercent(new BigDecimal("10")).build());
        // 40 % von 54 kWh effektiv = 21,6 kWh auf 120 km
        tripRepository.save(trip(c, "TESSIE", START.plusDays(2), 120).socStart(new BigDecimal("80")).socEnd(new BigDecimal("40")).build());

        assertEquals(0, new BigDecimal("18.00").compareTo(stats(c, u, START, END, null).driveConsumptionKwhPer100km()));
    }

    @Test
    void modellschnitt_abFuenfAnderenNutzern_kleinererWertGewinnt() {
        LocalDate end = LocalDate.of(2026, 9, 30), start = end.minusDays(27);
        CarBrand.CarModel model = CarBrand.CarModel.IONIQ_6;
        for (int i = 0; i < 5; i++) {
            Car peer = car(user(), model);
            kwhTrip(peer, "API_UPLOAD", start.plusDays(1 + i), 150, 24.0); // 16 kWh/100 km
        }
        User u = user();
        Car c = car(u, model);

        DriveStatistics s = stats(c, u, start, end, "22.0");

        assertEquals(0, new BigDecimal("16.00").compareTo(s.modelDriveConsumptionKwhPer100km()));
        assertEquals(5, s.modelDriveUsers());
        assertEquals(RangeBasis.Source.MODEL_DRIVE, s.rangeBasis().source());
        assertEquals(RangeBasis.Source.OWN_CHARGES, stats(c, u, start, end, "15.0").rangeBasis().source());
    }

    @Test
    void modellschnitt_ohneEigeneAutosUndOhneSeedNutzer() {
        LocalDate end = LocalDate.of(2026, 8, 31), start = end.minusDays(27);
        CarBrand.CarModel model = CarBrand.CarModel.ZOE;
        for (int i = 0; i < 4; i++) {
            kwhTrip(car(user(), model), "API_UPLOAD", start.plusDays(1 + i), 150, 30.0);
        }
        User seed = userRepository.save(user().toBuilder().seedData(true).build());
        kwhTrip(car(seed, model), "API_UPLOAD", start.plusDays(6), 150, 30.0);
        User u = user();
        Car own = car(u, model);
        kwhTrip(car(u, model), "API_UPLOAD", start.plusDays(7), 150, 30.0); // zweites eigenes Auto

        DriveStatistics s = stats(own, u, start, end, "25.0");

        assertNull(s.modelDriveConsumptionKwhPer100km());
        assertEquals(4, s.modelDriveUsers());
    }

    @Test
    void ohneZeitraumEnde_keinModellschnittUndKeineReichweitenbasis() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(3), 150, 30.0);

        DriveStatistics s = stats(c, u, null, null, "30.0");

        assertEquals(0, new BigDecimal("20.00").compareTo(s.driveConsumptionKwhPer100km()));
        assertNull(s.rangeBasis());
        assertNull(s.modelDriveConsumptionKwhPer100km());
    }

    @Test
    void saisonwerte_alleJahre() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2024, 12, 10), 60, 15.0);
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2025, 1, 10), 60, 15.0); // Winter: 30 / 120 = 25
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2025, 7, 10), 100, 15.0);
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2026, 6, 10), 100, 17.0); // Sommer: 32 / 200 = 16
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2025, 10, 10), 300, 60.0); // Oktober: keine Saison

        DriveStatistics s = stats(c, u, START, END, null);

        assertEquals(0, new BigDecimal("25.00").compareTo(s.driveWinterConsumptionKwhPer100km()));
        assertEquals(0, new BigDecimal("16.00").compareTo(s.driveSummerConsumptionKwhPer100km()));
    }

    @Test
    void modellWinter_andererFahrer() {
        CarBrand.CarModel model = CarBrand.CarModel.MG4;
        for (int i = 0; i < 5; i++) {
            Car peer = car(user(), model);
            kwhTrip(peer, "API_UPLOAD", LocalDate.of(2025, 12, 1 + i), 120, 27.0); // 22,5
            kwhTrip(peer, "API_UPLOAD", LocalDate.of(2025, 7, 1 + i), 120, 18.0); // Sommer zählt nicht
        }
        User u = user();
        Car own = car(u, model);

        assertEquals(0, new BigDecimal("22.50").compareTo(service.modelWinterConsumption(own, u.getId())));
    }

    @Test
    void schalterLadungen_setztReichweiteZurueck() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(3), 150, 30.0);
        service.setRangeBasisMode("charges");
        try {
            DriveStatistics s = stats(c, u, START, END, "49.5");
            assertEquals(RangeBasis.Source.OWN_CHARGES, s.rangeBasis().source());
            assertEquals(0, new BigDecimal("20.00").compareTo(s.driveConsumptionKwhPer100km()));
        } finally {
            service.setRangeBasisMode("drive");
        }
    }

    @Test
    void statistik_liefertFahrverbrauchFlachImJson_auchOhneLadungen() throws Exception {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(3), 150, 30.0);

        EvLogStatisticsResponse r = statisticsService.getStatistics(c.getId(), u.getId(), START, END, "MONTH");
        com.fasterxml.jackson.databind.JsonNode json = objectMapper.valueToTree(r);

        assertEquals(20.0, json.get("driveConsumptionKwhPer100km").asDouble(), 0.001);
        assertEquals("OWN_DRIVE", json.get("rangeBasis").get("source").asText());
        assertFalse(json.has("drive"));
    }

    @Test
    void monatswerte_nurMonateMitVerlaesslichemWert() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2026, 8, 5), 150, 27.0); // 18
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2026, 9, 5), 50, 10.0); // unter 100 km: fehlt
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2026, 10, 5), 200, 44.0); // 22

        DriveStatistics s = stats(c, u, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 31), null);

        assertEquals(java.util.List.of(
                new DriveStatistics.MonthDrive("2026-08", new BigDecimal("18.00")),
                new DriveStatistics.MonthDrive("2026-10", new BigDecimal("22.00"))), s.driveMonths());
    }

    @Test
    void saison_inUtcWieDieMonatswerte() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        // 01.05. 00:30 in Berlin ist der 30.04. in UTC: kein Sommer, wie in driveMonths ("2026-04")
        OffsetDateTime at = OffsetDateTime.parse("2026-05-01T00:30:00+02:00");
        tripRepository.save(trip(c, "TESLA_LIVE", LocalDate.of(2026, 5, 1), 150).tripStartedAt(at).tripEndedAt(at.plusHours(2))
                .estimatedConsumedKwh(new BigDecimal("27")).build());

        DriveStatistics s = stats(c, u, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31), null);

        assertNull(s.driveSummerConsumptionKwhPer100km());
        assertEquals("2026-04", s.driveMonths().get(0).month());
    }
}
