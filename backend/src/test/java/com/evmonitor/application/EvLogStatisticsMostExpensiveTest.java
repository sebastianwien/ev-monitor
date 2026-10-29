package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Die Story-Zeile "Deine teuerste Ladung" öffnet genau diese Ladung: dafür liefert die Statistik ihre id mit. */
class EvLogStatisticsMostExpensiveTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogStatisticsService evLogStatisticsService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("expensive-test-" + System.currentTimeMillis() + "@example.com");
        userId = user.getId();
        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "EXP-1",
                "Standard", new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();
    }

    private EvLog save(String cost, int daysAgo) {
        return evLogRepository.save(EvLog.createNew(carId, new BigDecimal("30.0"), cost == null ? null : new BigDecimal(cost),
                60, "u33d1", null, new BigDecimal("11.0"), null,
                LocalDateTime.now().minusDays(daysAgo), ChargingType.AC, null, null, false, null));
    }

    @Test
    void mostExpensiveCharge_carriesIdOfThatLog() {
        save("5.00", 3);
        EvLog expensive = save("14.58", 2);
        save(null, 1);

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertEquals(0, new BigDecimal("14.58").compareTo(stats.mostExpensiveChargeEur()));
        assertEquals(expensive.getId(), stats.mostExpensiveChargeLogId());
    }

    @Test
    void mostExpensiveCharge_noPricedLogs_idIsNull() {
        save(null, 1);

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertNull(stats.mostExpensiveChargeLogId());
    }
}
