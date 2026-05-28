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

class EvLogStatisticsChargingEfficiencySplitTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogStatisticsService evLogStatisticsService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("efficiency-test-" + System.currentTimeMillis() + "@example.com");
        userId = user.getId();
        Car car = Car.createNew(userId, CarBrand.CarModel.MODEL_3, 2023, "EFF-1",
                "Standard", new BigDecimal("75.0"), new BigDecimal("275.0"), null);
        carRepository.save(car);
        carId = car.getId();
    }

    @Test
    void efficiencySplit_bothFieldsPresent_sumsCorrectly() {
        // log with both kwhCharged and kwhAtVehicle — counts toward efficiency
        saveLogWithBothKwh(new BigDecimal("100.0"), new BigDecimal("88.0"));
        saveLogWithBothKwh(new BigDecimal("50.0"), new BigDecimal("46.0"));

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertNotNull(stats.chargingEfficiencySplit());
        assertEquals(0, new BigDecimal("150.0").compareTo(stats.chargingEfficiencySplit().gridKwh()));
        assertEquals(0, new BigDecimal("134.0").compareTo(stats.chargingEfficiencySplit().vehicleKwh()));
        assertEquals(2, stats.chargingEfficiencySplit().coveredLogCount());
    }

    @Test
    void efficiencySplit_onlyKwhCharged_notCounted() {
        // log with only kwhCharged — cannot compute efficiency
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), null,
                60, "u33d1", null, new BigDecimal("11.0"), null,
                LocalDateTime.now().minusDays(1), ChargingType.AC, null, null, false, null));

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertNotNull(stats.chargingEfficiencySplit());
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.chargingEfficiencySplit().gridKwh()));
        assertEquals(0, stats.chargingEfficiencySplit().coveredLogCount());
    }

    @Test
    void efficiencySplit_excludedLogs_notCounted() {
        EvLog log = buildLogWithBothKwh(new BigDecimal("100.0"), new BigDecimal("88.0"));
        evLogRepository.save(log.withIncludeInStatistics(false));

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertNotNull(stats.chargingEfficiencySplit());
        assertEquals(0, stats.chargingEfficiencySplit().coveredLogCount());
    }

    @Test
    void efficiencySplit_totalLogCount_reflectsAllStatLogs() {
        saveLogWithBothKwh(new BigDecimal("100.0"), new BigDecimal("90.0"));
        // log without kwhAtVehicle — counted in total but not in covered
        evLogRepository.save(EvLog.createNew(carId, new BigDecimal("40.0"), null,
                60, "u33d2", null, new BigDecimal("11.0"), null,
                LocalDateTime.now().minusDays(1), ChargingType.AC, null, null, false, null));

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(carId, userId, null, null, null);

        assertEquals(1, stats.chargingEfficiencySplit().coveredLogCount());
        assertEquals(2, stats.chargingEfficiencySplit().totalLogCount());
    }

    private void saveLogWithBothKwh(BigDecimal kwhCharged, BigDecimal kwhAtVehicle) {
        evLogRepository.save(buildLogWithBothKwh(kwhCharged, kwhAtVehicle));
    }

    private EvLog buildLogWithBothKwh(BigDecimal kwhCharged, BigDecimal kwhAtVehicle) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(kwhCharged)
                .kwhAtVehicle(kwhAtVehicle)
                .costEur(new BigDecimal("10.00"))
                .chargeDurationMinutes(60)
                .geohash("u33d1")
                .maxChargingPowerKw(new BigDecimal("11.0"))
                .loggedAt(LocalDateTime.now().minusDays(2))
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(ChargingType.AC)
                .publicCharging(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
