package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for community-facing consumption methods:
 * - calculateCommunityAvgConsumption()
 * - calculateSeasonalConsumption()
 *
 * These methods share the same SoC→fallback logic as getStatistics() but operate
 * across multiple cars and bucket results by season. Tested separately because they
 * have no other callers and were previously untested.
 */
class EvLogServiceCommunityConsumptionTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        User user = createAndSaveUser("community-" + System.currentTimeMillis() + "@example.com");
        userId = user.getId();
    }

    // -------------------------------------------------------------------------
    // calculateCommunityAvgConsumption
    // -------------------------------------------------------------------------

    @Test
    void communityAvg_singleCar_socPath_returnsCorrectDistanceWeightedAvg() {
        // logX: odometer 10000, soc 80%
        // logY: odometer 10300, kwh 52.5, soc 85%
        // socBefore(logY) = 85 - (52.5/75*100) = 15%
        // energyConsumed  = (80 - 15) * 75/100 = 48.75 kWh
        // consumption     = 48.75/300*100 = 16.25 kWh/100km
        Car car = carWithBattery(new BigDecimal("75.0"));
        saveLog(car.getId(), 10000, new BigDecimal("45.0"), 80, LocalDateTime.now().minusDays(2));
        saveLog(car.getId(), 10300, new BigDecimal("52.5"), 85, LocalDateTime.now().minusDays(1));

        CommunityConsumptionResult result = evLogService.calculateCommunityAvgConsumption(List.of(car), false);

        assertNotNull(result.value());
        assertEquals(new BigDecimal("16.25"), result.value());
        assertEquals(1, result.tripCount());
    }

    @Test
    void communityAvg_twoCars_distanceWeightedAcrossCars() {
        // Car A: 300km, 16.25 kWh/100km
        Car carA = carWithBattery(new BigDecimal("75.0"));
        saveLog(carA.getId(), 10000, new BigDecimal("45.0"), 80, LocalDateTime.now().minusDays(2));
        saveLog(carA.getId(), 10300, new BigDecimal("52.5"), 85, LocalDateTime.now().minusDays(1));

        // Car B: 150km, 15.00 kWh/100km (partial charge)
        // socBefore(logY) = 90 - (30/75*100) = 50% → energyConsumed = (80-50)*75/100 = 22.5 kWh
        Car carB = carWithBattery(new BigDecimal("75.0"));
        saveLog(carB.getId(), 15000, new BigDecimal("45.0"), 80, LocalDateTime.now().minusDays(2));
        saveLog(carB.getId(), 15150, new BigDecimal("30.0"), 90, LocalDateTime.now().minusDays(1));

        CommunityConsumptionResult result = evLogService.calculateCommunityAvgConsumption(List.of(carA, carB), false);

        // (16.25 * 300 + 15.00 * 150) / 450 = 7125 / 450 = 15.83
        assertNotNull(result.value());
        assertEquals(new BigDecimal("15.83"), result.value());
        assertEquals(2, result.tripCount());
    }

    @Test
    void communityAvg_fallbackPath_whenNoSocData() {
        // Logs without SoC → fallback: kWh/distance
        Car car = carWithBattery(new BigDecimal("75.0"));
        saveLogNoSoc(car.getId(), 10000, new BigDecimal("20.0"), LocalDateTime.now().minusDays(2));
        saveLogNoSoc(car.getId(), 10100, new BigDecimal("20.0"), LocalDateTime.now().minusDays(1));

        CommunityConsumptionResult result = evLogService.calculateCommunityAvgConsumption(List.of(car), false);

        // 20 kWh / 100km * 100 = 20 kWh/100km
        assertNotNull(result.value());
        assertEquals(new BigDecimal("20.00"), result.value());
        assertEquals(1, result.tripCount());
    }

    @Test
    void communityAvg_hybridPath_combinesSocAndFallback() {
        // Hybrid scenario: Car has BOTH SoC-based logs (newer) AND logs without SoC (older, fallback)
        // This simulates Sprit-Monitor imports before/after SoC field was added (Nov 2023)
        Car car = carWithBattery(new BigDecimal("75.0"));

        // Older logs WITHOUT SoC (before Nov 2023): 10000-10100km = 100km, 20 kWh → 20 kWh/100km (fallback)
        saveLogNoSoc(car.getId(), 10000, new BigDecimal("20.0"), LocalDateTime.now().minusDays(10));
        saveLogNoSoc(car.getId(), 10100, new BigDecimal("20.0"), LocalDateTime.now().minusDays(9));

        // Newer logs WITH SoC (after Nov 2023): 10100-10400km = 300km → 16.25 kWh/100km (SoC-based)
        // socBefore(logY) = 85 - (52.5/75*100) = 15%
        // energyConsumed  = (80 - 15) * 75/100 = 48.75 kWh
        // consumption     = 48.75/300*100 = 16.25 kWh/100km
        saveLog(car.getId(), 10100, new BigDecimal("45.0"), 80, LocalDateTime.now().minusDays(2));
        saveLog(car.getId(), 10400, new BigDecimal("52.5"), 85, LocalDateTime.now().minusDays(1));

        CommunityConsumptionResult result = evLogService.calculateCommunityAvgConsumption(List.of(car), false);

        // Distance-weighted average: (20*100 + 16.25*300) / 400 = (2000 + 4875) / 400 = 6875 / 400 = 17.19
        assertNotNull(result.value());
        assertEquals(new BigDecimal("17.19"), result.value());
        assertEquals(2, result.tripCount(), "Both SoC-based and fallback trips should be counted");
    }

    @Test
    void communityAvg_emptyCarList_returnsEmpty() {
        CommunityConsumptionResult result = evLogService.calculateCommunityAvgConsumption(List.of(), false);
        assertNull(result.value());
        assertEquals(0, result.tripCount());
    }

    @Test
    void communityAvg_carWithNoLogs_returnsEmpty() {
        Car car = carWithBattery(new BigDecimal("75.0"));
        CommunityConsumptionResult result = evLogService.calculateCommunityAvgConsumption(List.of(car), false);
        assertNull(result.value());
        assertEquals(0, result.tripCount());
    }

    // -------------------------------------------------------------------------
    // calculateSeasonalConsumption
    // -------------------------------------------------------------------------

    @Test
    void seasonal_summerLog_bucketedCorrectly() {
        Car car = carWithBattery(new BigDecimal("75.0"));
        // logY is in June (month 6 = summer)
        LocalDateTime june = LocalDateTime.of(2025, 6, 1, 10, 0);
        saveLog(car.getId(), 10000, new BigDecimal("45.0"), 80, june);
        saveLog(car.getId(), 10300, new BigDecimal("52.5"), 85, june.plusDays(1));

        EvLogService.SeasonalConsumptionResult result =
                evLogService.calculateSeasonalConsumption(List.of(car), false);

        assertNotNull(result.summerConsumptionKwhPer100km());
        assertEquals(new BigDecimal("16.25"), result.summerConsumptionKwhPer100km());
        assertNull(result.winterConsumptionKwhPer100km(), "No winter data");
        assertEquals(300, result.summerKm());
        assertEquals(0, result.winterKm());
        assertEquals(1, result.summerLogCount());
        assertEquals(0, result.winterLogCount());
    }

    @Test
    void seasonal_winterLog_bucketedCorrectly() {
        Car car = carWithBattery(new BigDecimal("75.0"));
        // logY is in January (month 1 = winter)
        LocalDateTime january = LocalDateTime.of(2025, 1, 1, 10, 0);
        saveLog(car.getId(), 10000, new BigDecimal("45.0"), 80, january);
        saveLog(car.getId(), 10300, new BigDecimal("52.5"), 85, january.plusDays(1));

        EvLogService.SeasonalConsumptionResult result =
                evLogService.calculateSeasonalConsumption(List.of(car), false);

        assertNull(result.summerConsumptionKwhPer100km(), "No summer data");
        assertNotNull(result.winterConsumptionKwhPer100km());
        assertEquals(new BigDecimal("16.25"), result.winterConsumptionKwhPer100km());
        assertEquals(0, result.summerKm());
        assertEquals(300, result.winterKm());
    }

    @Test
    void seasonal_mixed_totalIsDistanceWeightedAcrossSeasons() {
        Car car = carWithBattery(new BigDecimal("75.0"));

        // Winter trip (January): logX Jan 1, logY Jan 2 → 150km, 15.00 kWh/100km
        LocalDateTime jan1 = LocalDateTime.of(2025, 1, 1, 10, 0);
        saveLog(car.getId(), 20000, new BigDecimal("45.0"), 80, jan1);
        saveLog(car.getId(), 20150, new BigDecimal("30.0"), 90, jan1.plusDays(1));

        // Summer trip (June): logX Jun 1, logY Jun 2 → 300km, 16.25 kWh/100km
        LocalDateTime jun1 = LocalDateTime.of(2025, 6, 1, 10, 0);
        saveLog(car.getId(), 10000, new BigDecimal("45.0"), 80, jun1);
        saveLog(car.getId(), 10300, new BigDecimal("52.5"), 85, jun1.plusDays(1));

        EvLogService.SeasonalConsumptionResult result =
                evLogService.calculateSeasonalConsumption(List.of(car), false);

        assertEquals(new BigDecimal("16.25"), result.summerConsumptionKwhPer100km());
        assertEquals(new BigDecimal("15.00"), result.winterConsumptionKwhPer100km());
        // Total: (16.25*300 + 15.00*150) / 450 = 7125/450 = 15.83
        assertEquals(new BigDecimal("15.83"), result.totalConsumptionKwhPer100km());
        assertEquals(300, result.summerKm());
        assertEquals(150, result.winterKm());
    }

    @Test
    void seasonal_emptyCarList_returnsNullConsumptions() {
        EvLogService.SeasonalConsumptionResult result =
                evLogService.calculateSeasonalConsumption(List.of(), false);

        assertNull(result.summerConsumptionKwhPer100km());
        assertNull(result.winterConsumptionKwhPer100km());
        assertNull(result.totalConsumptionKwhPer100km());
        assertEquals(0, result.summerKm());
        assertEquals(0, result.winterKm());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Car carWithBattery(BigDecimal batteryKwh) {
        Car car = Car.createNew(
                userId, CarBrand.CarModel.MODEL_3, 2023,
                "T-" + UUID.randomUUID().toString().substring(0, 8),
                "Long Range", batteryKwh, new BigDecimal("275.0"));
        return carRepository.save(car);
    }

    private void saveLog(UUID carId, int odometerKm, BigDecimal kwhCharged,
                         int socAfter, LocalDateTime loggedAt) {
        evLogRepository.save(EvLog.createNew(
                carId, kwhCharged, new BigDecimal("10.00"), 60,
                "u33d1", odometerKm, new BigDecimal("11.0"), socAfter, loggedAt, ChargingType.UNKNOWN));
    }

    private void saveLogNoSoc(UUID carId, int odometerKm, BigDecimal kwhCharged,
                               LocalDateTime loggedAt) {
        evLogRepository.save(EvLog.createNew(
                carId, kwhCharged, new BigDecimal("10.00"), 60,
                "u33d1", odometerKm, new BigDecimal("11.0"), null, loggedAt, ChargingType.UNKNOWN));
    }
}
