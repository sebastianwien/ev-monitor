package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractServiceTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Die Statistik lädt die Ladungen eines Autos genau einmal je Aufruf. Die Story ruft sie
 * mehrfach je Öffnen, jede zweite Ladung der Historie wäre doppelte Datenbanklast.
 */
@Transactional
class EvLogStatisticsQueryCountTest extends AbstractServiceTest {

    private static final String LOGS_BY_CAR = "SELECT e FROM EvLogEntity e WHERE e.carId = :carId";

    @Autowired
    private EvLogStatisticsService evLogStatisticsService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void periodStatisticsLoadsCarLogsOnce() {
        User user = createAndSaveUser("querycount@ev-monitor.net");
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
        for (int i = 0; i < 3; i++) {
            EvLog log = EvLog.createNew(car.getId(), new BigDecimal("30"), null, 60, null, null, null, null,
                    LocalDate.of(2026, 3, 5 + i).atStartOfDay(), null, null, null, false, null);
            evLogRepository.save(log.toBuilder().odometerKm(10_000 + i * 200).includeInStatistics(true).build());
        }
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        evLogStatisticsService.getStatistics(car.getId(), user.getId(),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "DAY");

        long executions = Arrays.stream(stats.getQueries())
                .filter(LOGS_BY_CAR::equals)
                .mapToLong(q -> stats.getQueryStatistics(q).getExecutionCount())
                .sum();
        assertEquals(1, executions, "Ladungen des Autos je Statistik-Aufruf einmal laden");
    }
}
