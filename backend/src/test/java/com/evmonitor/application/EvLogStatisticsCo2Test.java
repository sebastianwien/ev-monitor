package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractServiceTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CO₂ in der Statistik: Bilanz des Zeitraums aus den Tacho-Abschnitten, deren schließende Ladung im
 * Zeitraum liegt, und je Punkt der Zeitreihe die Ersparnis der dort schließenden Abschnitte.
 */
@Transactional
class EvLogStatisticsCo2Test extends AbstractServiceTest {

    @Autowired
    private EvLogStatisticsService service;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User user;
    private Car car;

    private static final double LITERS = VehicleCategory.SEDAN.getPetrolLitersPer100Km().doubleValue();

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("co2-test@ev-monitor.net");
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
    }

    @Test
    void zeitraumZaehltNurAbschnitteMitSchliessenderLadungImZeitraum() {
        saveLog(LocalDate.of(2025, 1, 1), "30", 10_000);
        saveLog(LocalDate.of(2025, 1, 10), "36", 10_200);
        saveLog(LocalDate.of(2025, 2, 5), "30", 10_400);

        EvLogStatisticsResponse.Co2Statistics co2 = stats(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), "MONTH").co2();

        assertNotNull(co2);
        assertEquals(200, co2.distanceKm());
        double petrol = 200 * LITERS / 100 * Co2Factors.PETROL_KG_CO2E_PER_LITER;
        double electricity = 30 * 406 / 1000.0;
        assertEquals(petrol, co2.petrolKg().doubleValue(), 0.01);
        assertEquals(electricity, co2.electricityKg().doubleValue(), 0.01);
        assertEquals(petrol - electricity, co2.savedKg().doubleValue(), 0.01);
        assertEquals(200 * LITERS / 100, co2.petrolLiters().doubleValue(), 0.01);
        assertEquals(LITERS, co2.petrolLitersPer100km().doubleValue(), 0.001);
    }

    @Test
    void lifetimeHatBilanzUndErsparnisJePunktDerZeitreihe() {
        saveLog(LocalDate.of(2025, 1, 1), "30", 10_000);
        saveLog(LocalDate.of(2025, 1, 10), "36", 10_200);
        saveLog(LocalDate.of(2025, 2, 5), "30", 10_400);

        EvLogStatisticsResponse response = stats(null, null, "DAY");

        assertEquals(400, response.co2().distanceKm());
        List<EvLogStatisticsResponse.ChargeDataPoint> points = response.chargesOverTime();
        assertEquals(3, points.size());
        assertNull(points.get(0).co2SavedKg(), "Anker ohne Strecke");
        double first = 200 * LITERS / 100 * Co2Factors.PETROL_KG_CO2E_PER_LITER - 36 * 406 / 1000.0;
        double second = 200 * LITERS / 100 * Co2Factors.PETROL_KG_CO2E_PER_LITER - 30 * 406 / 1000.0;
        assertEquals(first, points.get(1).co2SavedKg().doubleValue(), 0.01);
        assertEquals(second, points.get(2).co2SavedKg().doubleValue(), 0.01);
        assertEquals(first + second, response.co2().savedKg().doubleValue(), 0.01);
    }

    @Test
    void ohneTachostandKeineBilanz() {
        saveLog(LocalDate.of(2025, 1, 1), "30", null);
        saveLog(LocalDate.of(2025, 1, 10), "36", null);

        assertNull(stats(null, null, "MONTH").co2());
    }

    private EvLogStatisticsResponse stats(LocalDate from, LocalDate to, String groupBy) {
        entityManager.flush();
        entityManager.clear();
        return service.getStatistics(car.getId(), user.getId(), from, to, groupBy);
    }

    private void saveLog(LocalDate date, String kwh, Integer odometerKm) {
        EvLog log = EvLog.createNew(car.getId(), new BigDecimal(kwh), null,
                60, null, null, null, null,
                date.atTime(8, 0), null, null, null, false, null);
        evLogRepository.save(log.toBuilder().odometerKm(odometerKm).includeInStatistics(true).build());
    }
}
