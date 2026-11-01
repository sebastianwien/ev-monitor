package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class Co2CalculatorTest {

    private static final double EPS = 1e-6;
    /** Grenzen auf Netzseite wie in der Anwendung: 10 bis 40 / 0,87 kWh je 100 km */
    private final Co2Calculator calculator = new Co2Calculator(10.0, 40.0 / 0.87);
    /** Netz-kWh wie gemessen an der Säule, sonst null */
    private static final Function<EvLog, BigDecimal> AT_CHARGER = EvLog::getKwhCharged;

    private static final double PETROL = Co2Factors.PETROL_KG_CO2E_PER_LITER;
    private static final double SEDAN_L = VehicleCategory.SEDAN.getPetrolLitersPer100Km().doubleValue();

    @Test
    void abschnittZwischenZweiTachostaendenRechnetKmGegenNetzKwhDerSchliessendenLadung() {
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                log(at(2025, 3, 8), 36, 10_200));

        List<Co2Segment> segments = calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER);

        assertEquals(1, segments.size());
        Co2Segment s = segments.get(0);
        assertEquals(logs.get(1).getId(), s.closingLogId());
        assertEquals(logs.get(1).getLoggedAt(), s.closedAt());
        assertEquals(200, s.distanceKm());
        assertEquals(36.0, s.gridKwh(), EPS);
        assertEquals(200 * SEDAN_L / 100, s.petrolLiters(), EPS);
        assertEquals(200 * SEDAN_L / 100 * PETROL, s.petrolKg(), EPS);
        assertEquals(36.0 * 406 / 1000, s.electricityKg(), EPS);
        assertEquals(s.petrolKg() - s.electricityKg(), s.savedKg(), EPS);
    }

    @Test
    void ersteLadungMitTachoIstNurAnkerUndZaehltNicht() {
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 20, null),
                log(at(2025, 3, 2), 30, 10_000));

        assertTrue(calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER).isEmpty());
    }

    @Test
    void ladungOhneTachoZwischenZweiTachostaendenZaehltImAbschnitt() {
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                log(at(2025, 3, 4), 20, null),
                log(at(2025, 3, 8), 25, 10_300));

        List<Co2Segment> segments = calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER);

        assertEquals(1, segments.size());
        assertEquals(300, segments.get(0).distanceKm());
        assertEquals(45.0, segments.get(0).gridKwh(), EPS);
        assertEquals(logs.get(2).getId(), segments.get(0).closingLogId());
    }

    @Test
    void teilladungAmGleichenTachostandWirdMitDemNaechstenAbschnittGezaehlt() {
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                log(at(2025, 3, 8), 30, 10_200),
                log(at(2025, 3, 8, 13), 6, 10_200),
                log(at(2025, 3, 15), 30, 10_400));

        List<Co2Segment> segments = calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER);

        assertEquals(2, segments.size());
        assertEquals(30.0, segments.get(0).gridKwh(), EPS);
        assertEquals(200, segments.get(1).distanceKm());
        assertEquals(36.0, segments.get(1).gridKwh(), EPS);
    }

    @Test
    void netzKwhKommenAusDerUebergebenenFunktion() {
        EvLog anchor = log(at(2025, 3, 1), 30, 10_000);
        EvLog vehicleSide = EvLog.builder()
                .id(UUID.randomUUID()).carId(anchor.getCarId())
                .kwhAtVehicle(new BigDecimal("27")).odometerKm(10_200)
                .loggedAt(at(2025, 3, 8)).includeInStatistics(true)
                .dataSource(DataSource.USER_LOGGED).build();
        Function<EvLog, BigDecimal> grid = l -> l.getKwhCharged() != null
                ? l.getKwhCharged() : l.getKwhAtVehicle().divide(new BigDecimal("0.9"));

        List<Co2Segment> segments = calculator.segments(List.of(anchor, vehicleSide), VehicleCategory.SEDAN, grid);

        assertEquals(30.0, segments.get(0).gridKwh(), EPS);
    }

    @Test
    void tachoRueckwaertsVerwirftDenAbschnittUndSetztNeuAn() {
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                log(at(2025, 3, 8), 30, 9_800),
                log(at(2025, 3, 15), 32, 10_000));

        List<Co2Segment> segments = calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER);

        assertEquals(1, segments.size());
        assertEquals(200, segments.get(0).distanceKm());
        assertEquals(32.0, segments.get(0).gridKwh(), EPS);
    }

    @Test
    void abschnittMitZuWenigStromFehltBeidseitig() {
        // 600 km mit 15 kWh: dazwischen fehlen Ladungen, sonst wären es Benziner-km ohne Strom
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                log(at(2025, 3, 20), 15, 10_600));

        assertTrue(calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER).isEmpty());
    }

    @Test
    void abschnittMitZuVielStromFehltBeidseitig() {
        // 50 km mit 40 kWh: Tachofehler
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                log(at(2025, 3, 2), 40, 10_050));

        assertTrue(calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER).isEmpty());
    }

    @Test
    void ladungOhneEnergieVerwirftDenAbschnitt() {
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                log(at(2025, 3, 4), null, null),
                log(at(2025, 3, 8), 30, 10_200));

        assertTrue(calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER).isEmpty());
    }

    @Test
    void ladungAusserhalbDerAuswertungZaehltNicht() {
        EvLog excluded = log(at(2025, 3, 4), 50, null).toBuilder().includeInStatistics(false).build();
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                excluded,
                log(at(2025, 3, 8), 30, 10_200));

        List<Co2Segment> segments = calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER);

        assertEquals(30.0, segments.get(0).gridKwh(), EPS);
    }

    @Test
    void stromFaktorNachJahrDerLadungUeberDieJahresgrenze() {
        List<EvLog> logs = List.of(
                log(at(2022, 12, 20), 30, 10_000),
                log(at(2022, 12, 28), 20, null),
                log(at(2023, 1, 5), 20, 10_250));

        Co2Segment s = calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER).get(0);

        assertEquals((20 * 503 + 20 * 442) / 1000.0, s.electricityKg(), EPS);
        assertEquals(2023, s.closedAt().getYear());
    }

    @Test
    void ungeordneteLadungenWerdenNachZeitSortiert() {
        List<EvLog> logs = new ArrayList<>(List.of(
                log(at(2025, 3, 8), 36, 10_200),
                log(at(2025, 3, 1), 30, 10_000)));

        assertEquals(200, calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER).get(0).distanceKm());
    }

    @Test
    void mehrStromAlsBenzinerErgibtNegativeErsparnisOhneAbschneiden() {
        // Kleinwagen, 42 kWh je 100 km im Jahr 2022
        List<EvLog> logs = List.of(
                log(at(2022, 1, 1), 30, 10_000),
                log(at(2022, 1, 5), 42, 10_100));

        Co2Segment s = calculator.segments(logs, VehicleCategory.CITY_CAR, AT_CHARGER).get(0);

        assertTrue(s.savedKg() < 0, "savedKg=" + s.savedKg());
    }

    @Test
    void bilanzSummiertAbschnitteUndIstOhneAbschnitteNull() {
        List<EvLog> logs = List.of(
                log(at(2025, 3, 1), 30, 10_000),
                log(at(2025, 3, 8), 36, 10_200),
                log(at(2025, 3, 15), 30, 10_400));
        List<Co2Segment> segments = calculator.segments(logs, VehicleCategory.SEDAN, AT_CHARGER);

        Co2Balance b = Co2Balance.of(segments);

        assertEquals(400, b.distanceKm());
        assertEquals(segments.get(0).petrolKg() + segments.get(1).petrolKg(), b.petrolKg(), EPS);
        assertEquals(segments.get(0).electricityKg() + segments.get(1).electricityKg(), b.electricityKg(), EPS);
        assertEquals(b.petrolKg() - b.electricityKg(), b.savedKg(), EPS);
        assertEquals(400 * SEDAN_L / 100, b.petrolLiters(), EPS);
        assertNull(Co2Balance.of(List.of()));
    }

    private static LocalDateTime at(int y, int m, int d) {
        return at(y, m, d, 8);
    }

    private static LocalDateTime at(int y, int m, int d, int hour) {
        return LocalDateTime.of(y, m, d, hour, 0);
    }

    private static EvLog log(LocalDateTime when, Integer kwhCharged, Integer odometerKm) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .kwhCharged(kwhCharged == null ? null : BigDecimal.valueOf(kwhCharged))
                .odometerKm(odometerKm)
                .loggedAt(when)
                .includeInStatistics(true)
                .dataSource(DataSource.USER_LOGGED)
                .build();
    }
}
