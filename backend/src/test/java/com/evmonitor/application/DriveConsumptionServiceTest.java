package com.evmonitor.application;

import com.evmonitor.application.DriveConsumptionService.PeerQuery;
import com.evmonitor.application.DriveConsumptionService.Period;
import com.evmonitor.application.DriveConsumptionService.Season;
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
 * 20 km. Der Schnitt anderer Fahrer zählt Nutzer, nicht Autos, Variante vor Modell.
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

    private Car carOfVariant(User owner, CarBrand.CarModel model, UUID specId) {
        return carRepository.save(TestDataBuilder.createTestCar(owner.getId(), model, new BigDecimal("60.0"))
                .toBuilder().vehicleSpecificationId(specId).build());
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

    private DriveStatistics stats(Car car, User owner, LocalDate start, LocalDate end) {
        return service.forStatistics(car, owner, new Period(start, end));
    }

    @Test
    void model3Fall_fahrverbrauchOhneStand() {
        // Regression: 49,5 aus Ladungen (mit Stand), gefahren 23,8
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(3), 120, 28.56);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(10), 80, 19.04);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(11), 8, 4.0); // Kurzstrecke zählt nicht

        DriveStatistics s = stats(c, u, START, END);

        assertEquals(0, new BigDecimal("23.80").compareTo(s.driveConsumptionKwhPer100km()));
    }

    @Test
    void smartcarUndTeslaInferred_zaehlenNicht() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.POLESTAR_2);
        kwhTrip(c, "SMARTCAR_LIVE", START.plusDays(2), 150, 27.0);
        kwhTrip(c, "TESLA_INFERRED", START.plusDays(3), 150, 27.0);

        assertNull(stats(c, u, START, END).driveConsumptionKwhPer100km());
    }

    @Test
    void schattenGeloeschtUndFremderHalter_zaehlenNicht() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        tripRepository.save(trip(c, "TESLA_LIVE", START.plusDays(2), 150).estimatedConsumedKwh(new BigDecimal("30"))
                .deletedAt(OffsetDateTime.now()).build());
        tripRepository.save(trip(c, "TESLA_LIVE", START.plusDays(3), 150).estimatedConsumedKwh(new BigDecimal("30"))
                .userId(UUID.randomUUID()).build());

        assertNull(stats(c, u, START, END).driveConsumptionKwhPer100km());
    }

    @Test
    void enyaqFall_unsinnigeFahrten_keinWert() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.ENYAQ);
        kwhTrip(c, "API_UPLOAD", START.plusDays(3), 100, 60.0); // 60 kWh/100 km, Messfehler
        kwhTrip(c, "API_UPLOAD", START.plusDays(4), 100, 3.0);  // 3 kWh/100 km, Messfehler
        kwhTrip(c, "API_UPLOAD", START.plusDays(5), 50, 10.0);  // 20, aber allein unter 100 km

        assertNull(stats(c, u, START, END).driveConsumptionKwhPer100km());
    }

    @Test
    void socFahrt_mitEffektiverKapazitaet() {
        User u = user();
        Car c = carRepository.save(TestDataBuilder.createTestCar(u.getId(), CarBrand.CarModel.ID_3, new BigDecimal("60.0"))
                .toBuilder().batteryDegradationPercent(new BigDecimal("10")).build());
        // 60 % SoC-Abfall × 54 kWh effektiv = 32,4 kWh auf 150 km = 21,6
        tripRepository.save(trip(c, "API_UPLOAD", START.plusDays(3), 150)
                .socStart(new BigDecimal("90")).socEnd(new BigDecimal("30")).build());

        assertEquals(0, new BigDecimal("21.60").compareTo(stats(c, u, START, END).driveConsumptionKwhPer100km()));
    }

    @Test
    void modellschnitt_abZweiAnderenNutzern_fuerDiePeerKachel() {
        LocalDate end = LocalDate.of(2026, 9, 30), start = end.minusDays(27);
        CarBrand.CarModel model = CarBrand.CarModel.IONIQ_6;
        for (int i = 0; i < 2; i++) {
            Car peer = car(user(), model);
            kwhTrip(peer, "API_UPLOAD", start.plusDays(1 + i), 150, 24.0); // 16 kWh/100 km
        }
        User u = user();
        Car c = car(u, model);
        kwhTrip(c, "API_UPLOAD", start.plusDays(5), 150, 45.0); // eigenes Auto zählt nicht mit

        DriveStatistics s = stats(c, u, start, end);

        assertEquals(0, new BigDecimal("16.00").compareTo(s.modelDriveConsumptionKwhPer100km()));
        assertEquals(2, s.modelDriveUsers());
    }

    @Test
    void modellschnitt_ohneEigeneAutosUndOhneSeedNutzer() {
        LocalDate end = LocalDate.of(2026, 8, 31), start = end.minusDays(27);
        CarBrand.CarModel model = CarBrand.CarModel.ZOE;
        kwhTrip(car(user(), model), "API_UPLOAD", start.plusDays(1), 150, 30.0); // ein anderer Fahrer
        User seed = userRepository.save(user().toBuilder().seedData(true).build());
        kwhTrip(car(seed, model), "API_UPLOAD", start.plusDays(6), 150, 30.0);
        User u = user();
        Car own = car(u, model);
        kwhTrip(car(u, model), "API_UPLOAD", start.plusDays(7), 150, 30.0); // zweites eigenes Auto

        DriveStatistics s = stats(own, u, start, end);

        assertNull(s.modelDriveConsumptionKwhPer100km());
        assertEquals(1, s.modelDriveUsers());
    }

    @Test
    void ohneZeitraumEnde_keinModellschnitt() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(3), 150, 30.0);

        DriveStatistics s = stats(c, u, null, null);

        assertEquals(0, new BigDecimal("20.00").compareTo(s.driveConsumptionKwhPer100km()));
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

        DriveStatistics s = stats(c, u, START, END);

        assertEquals(0, new BigDecimal("25.00").compareTo(s.driveWinterConsumptionKwhPer100km()));
        assertEquals(0, new BigDecimal("16.00").compareTo(s.driveSummerConsumptionKwhPer100km()));
    }

    // --- Andere Fahrer: Variante vor Modell, Saison, Grenze bei 2 ---

    @Test
    void peerDrive_varianteVorModell_jeAbZweiAnderenFahrern() {
        CarBrand.CarModel model = CarBrand.CarModel.MG4;
        UUID spec = UUID.randomUUID(), otherSpec = UUID.randomUUID();
        for (int i = 0; i < 2; i++) kwhTrip(carOfVariant(user(), model, spec), "API_UPLOAD", START.plusDays(1 + i), 150, 24.0); // 16
        for (int i = 0; i < 2; i++) kwhTrip(carOfVariant(user(), model, otherSpec), "API_UPLOAD", START.plusDays(3 + i), 150, 33.0); // 22
        User u = user();
        kwhTrip(carOfVariant(u, model, spec), "API_UPLOAD", START.plusDays(6), 150, 45.0); // der Fragende

        PeerDrive variant = service.peerDrive(new PeerQuery(model, spec, new Period(START, END), Season.ALL, u.getId()));
        assertEquals(PeerDrive.Scope.VARIANT, variant.scope());
        assertEquals(0, new BigDecimal("16.00").compareTo(variant.consumptionKwhPer100km()));
        assertEquals(2, variant.users());

        // Unbekannte Variante: das Modell über alle vier anderen Fahrer, 114 kWh / 600 km = 19
        PeerDrive modelWide = service.peerDrive(new PeerQuery(model, UUID.randomUUID(), new Period(START, END), Season.ALL, u.getId()));
        assertEquals(PeerDrive.Scope.MODEL, modelWide.scope());
        assertEquals(0, new BigDecimal("19.00").compareTo(modelWide.consumptionKwhPer100km()));
        assertEquals(4, modelWide.users());
    }

    @Test
    void peerDrive_einAndererFahrer_reichtNicht_zweiSchon() {
        LocalDate end = LocalDate.of(2026, 7, 31), start = end.minusDays(27);
        CarBrand.CarModel model = CarBrand.CarModel.KONA_ELECTRIC;
        kwhTrip(car(user(), model), "API_UPLOAD", start.plusDays(1), 150, 24.0);
        User u = user();

        PeerDrive one = service.peerDrive(new PeerQuery(model, null, new Period(start, end), Season.ALL, u.getId()));
        assertFalse(one.hasValue());
        assertEquals(1, one.users());

        LocalDate end2 = LocalDate.of(2026, 6, 30), start2 = end2.minusDays(27);
        kwhTrip(car(user(), model), "API_UPLOAD", start2.plusDays(1), 150, 24.0);
        kwhTrip(car(user(), model), "API_UPLOAD", start2.plusDays(2), 150, 24.0);
        PeerDrive two = service.peerDrive(new PeerQuery(model, null, new Period(start2, end2), Season.ALL, u.getId()));
        assertTrue(two.hasValue());
        assertEquals(2, two.users());
    }

    @Test
    void peerDrive_winterAndererFahrer_alleJahre() {
        CarBrand.CarModel model = CarBrand.CarModel.MG5;
        for (int i = 0; i < 2; i++) {
            Car peer = car(user(), model);
            kwhTrip(peer, "API_UPLOAD", LocalDate.of(2025, 12, 1 + i), 120, 27.0); // 22,5
            kwhTrip(peer, "API_UPLOAD", LocalDate.of(2025, 7, 1 + i), 120, 18.0); // Sommer zählt nicht
        }

        PeerDrive winter = service.peerDrive(new PeerQuery(model, null, Period.ALL, Season.WINTER, user().getId()));
        assertEquals(0, new BigDecimal("22.50").compareTo(winter.consumptionKwhPer100km()));
        assertEquals(0, new BigDecimal("15.00").compareTo(
                service.peerDrive(new PeerQuery(model, null, Period.ALL, Season.SUMMER, null)).consumptionKwhPer100km()));
    }

    @Test
    void statistik_liefertFahrverbrauchFlachUndReichweiteGeschachteltImJson() throws Exception {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", START.plusDays(3), 150, 30.0);

        EvLogStatisticsResponse r = statisticsService.getStatistics(c.getId(), u.getId(), START, END, "MONTH");
        com.fasterxml.jackson.databind.JsonNode json = objectMapper.valueToTree(r);

        assertEquals(20.0, json.get("driveConsumptionKwhPer100km").asDouble(), 0.001);
        assertFalse(json.has("drive"));
        assertFalse(json.has("rangeBasis"));
        // 60 kWh × 0,9 × 100 / 20 = 270 km
        assertEquals("OWN_DRIVE", json.get("range").get("current").get("source").asText());
        assertEquals(270, json.get("range").get("current").get("rangeKm").asInt());
    }

    @Test
    void monatswerte_nurMonateMitVerlaesslichemWert() {
        User u = user();
        Car c = car(u, CarBrand.CarModel.MODEL_3);
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2026, 8, 5), 150, 27.0); // 18
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2026, 9, 5), 50, 10.0); // unter 100 km: fehlt
        kwhTrip(c, "TESLA_LIVE", LocalDate.of(2026, 10, 5), 200, 44.0); // 22

        DriveStatistics s = stats(c, u, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 31));

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

        DriveStatistics s = stats(c, u, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31));

        assertNull(s.driveSummerConsumptionKwhPer100km());
        assertEquals("2026-04", s.driveMonths().get(0).month());
    }
}
