package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractServiceTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Der Verbrauch eines Zeitraums zählt wie die Saisonwerte nur plausible Ladungspaare. Ein Paar mit
 * wenig Strecke und viel Stand (hier 50 kWh/100 km) zog den 4-Wochen-Schnitt sonst über die Grenze 40.
 */
@Transactional
class EvLogStatisticsPeriodPlausibilityTest extends AbstractServiceTest {

    @Autowired
    private EvLogStatisticsService statisticsService;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 1, 18, 0);

    private void log(UUID carId, int day, int odometerKm, String kwh) {
        LocalDateTime at = BASE.plusDays(day);
        evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID()).carId(carId)
                .kwhCharged(new BigDecimal(kwh)).costEur(new BigDecimal("10.00")).chargeDurationMinutes(60)
                .odometerKm(odometerKm).socAfterChargePercent(new BigDecimal("80"))
                .loggedAt(at).dataSource(DataSource.USER_LOGGED).includeInStatistics(true)
                .chargingType(ChargingType.AC).createdAt(at).updatedAt(at)
                .build());
    }

    @Test
    void zeitraumSchnitt_ohneUnplausiblePaare() {
        User u = createAndSaveUser("period-plaus-" + UUID.randomUUID() + "@example.com");
        Car car = carRepository.save(TestDataBuilder.createTestCar(u.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("60.0")));
        log(car.getId(), 0, 10_000, "20");
        log(car.getId(), 3, 10_166, "30"); // rund 18 kWh/100 km
        log(car.getId(), 6, 10_196, "15"); // 30 km mit viel Stand: rund 50 kWh/100 km, unplausibel

        EvLogStatisticsResponse r = statisticsService.getStatistics(car.getId(), u.getId(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28), "MONTH");

        assertNotNull(r.avgConsumptionKwhPer100km());
        assertTrue(r.avgConsumptionKwhPer100km().doubleValue() < 20,
                "nur das plausible Paar, war " + r.avgConsumptionKwhPer100km());
    }
}
