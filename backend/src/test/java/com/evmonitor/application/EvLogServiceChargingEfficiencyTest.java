package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.weather.TemperatureEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for charging efficiency normalization in EvLogService.
 *
 * AT_CHARGER data (wallbox) is normalized to AT_VEHICLE equivalent before consumption calculations:
 *   effectiveKwh = kwhCharged * efficiency   (AC: 0.90, DC: 0.95)
 *
 * AT_VEHICLE data (Smartcar, Tesla Live) is used as-is.
 *
 * For avgCostPerKwh the inverse correction is applied:
 *   effectiveKwhForCost = kwhCharged / efficiency   (AT_VEHICLE only)
 */
@ExtendWith(MockitoExtension.class)
class EvLogServiceChargingEfficiencyTest {

    @Mock private EvLogRepository evLogRepository;
    @Mock private CarRepository carRepository;
    @Mock private UserRepository userRepository;
    @Mock private CoinLogService coinLogService;
    @Mock private TemperatureEnrichmentService temperatureEnrichmentService;
    @Mock private VehicleSpecificationRepository vehicleSpecificationRepository;

    private EvLogService service;

    private static final BigDecimal BATTERY_75 = new BigDecimal("75.0");
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 1, 2, 10, 0);

    @BeforeEach
    void setUp() {
        // Use real efficiency values (AC=0.90, DC=0.95)
        service = new EvLogService(
                evLogRepository, carRepository, userRepository,
                coinLogService, temperatureEnrichmentService,
                vehicleSpecificationRepository, new PlausibilityProperties(),
                mock(com.evmonitor.domain.BatterySohRepository.class));
    }

    /**
     * AT_CHARGER log with AC charging (UNKNOWN type + private = AC default):
     * effectiveKwh = 50 * 0.90 = 45 kWh
     *
     * socBefore(logY) = 85 - (45/75*100) = 85 - 60 = 25%
     * energyConsumed  = (80 - 25) * 75/100 = 55 * 0.75 = 41.25 kWh
     * consumption     = 41.25 * 100 / 300 = 13.75 kWh/100km
     */
    @Test
    void atCharger_acDefault_appliesAcEfficiency() {
        EvLog logX = atChargerLog(10000, null, 80, ChargingType.UNKNOWN, false, T1);
        EvLog logY = atChargerLog(10300, new BigDecimal("50.0"), 85, ChargingType.UNKNOWN, false, T2);

        var result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("13.75"), result.get());
    }

    /**
     * AT_CHARGER log with DC charging:
     * effectiveKwh = 50 * 0.95 = 47.5 kWh
     *
     * socBefore(logY) = 85 - (47.5/75*100) = 85 - 63.33 = 21.67%
     * energyConsumed  = (80 - 21.67) * 75/100 = 58.33 * 0.75 = 43.75 kWh
     * consumption     = 43.75 * 100 / 300 = 14.58 kWh/100km
     */
    @Test
    void atCharger_dcExplicit_appliesDcEfficiency() {
        EvLog logX = atChargerLog(10000, null, 80, ChargingType.DC, false, T1);
        EvLog logY = atChargerLog(10300, new BigDecimal("50.0"), 85, ChargingType.DC, false, T2);

        var result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("14.58"), result.get());
    }

    /**
     * AT_VEHICLE log (Smartcar/Tesla): no efficiency correction — kwhCharged used as-is.
     *
     * socBefore(logY) = 85 - (50/75*100) = 85 - 66.67 = 18.33%
     * energyConsumed  = (80 - 18.33) * 75/100 = 61.67 * 0.75 = 46.25 kWh
     * consumption     = 46.25 * 100 / 300 = 15.42 kWh/100km
     */
    @Test
    void atVehicle_noEfficiencyCorrection() {
        EvLog logX = atVehicleLog(10000, null, 80, T1);
        EvLog logY = atVehicleLog(10300, new BigDecimal("50.0"), 85, T2);

        var result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("15.42"), result.get());
    }

    /**
     * UNKNOWN type + public charging → DC proxy.
     * Same result as explicit DC.
     */
    @Test
    void atCharger_unknownType_publicCharging_usesDcProxy() {
        // maxPowerKw=null + duration=null → inference cannot resolve → UNKNOWN + publicCharging → DC proxy in service
        EvLog logX = atChargerLog(10000, null,                   80, ChargingType.UNKNOWN, true, T1, null, null);
        EvLog logY = atChargerLog(10300, new BigDecimal("50.0"), 85, ChargingType.UNKNOWN, true, T2, null, null);

        var result = service.calculateConsumption(logX, logY, BATTERY_75);

        // Same as DC test above
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("14.58"), result.get());
    }

    /**
     * Fallback calculation applies AC efficiency for AT_CHARGER logs:
     * effectiveKwh = 20 * 0.90 = 18 kWh
     * consumption = 18 * 100 / 100 = 18.00 kWh/100km
     */
    @Test
    void fallback_atCharger_appliesAcEfficiency() {
        EvLog log = atChargerLog(null, new BigDecimal("20.0"), null, ChargingType.UNKNOWN, false, T1);

        BigDecimal result = service.calculateConsumptionFallback(List.of(log), new BigDecimal("100"));

        assertEquals(new BigDecimal("18.00"), result);
    }

    /**
     * Fallback calculation does NOT apply correction for AT_VEHICLE logs:
     * consumption = 20 * 100 / 100 = 20.00 kWh/100km
     */
    @Test
    void fallback_atVehicle_noCorrection() {
        EvLog log = atVehicleLog(null, new BigDecimal("20.0"), null, T1);

        BigDecimal result = service.calculateConsumptionFallback(List.of(log), new BigDecimal("100"));

        assertEquals(new BigDecimal("20.00"), result);
    }

    // ── effectiveKwhForCost ───────────────────────────────────────────────────

    /**
     * AT_CHARGER log: effectiveKwhForCost returns kwhCharged unchanged.
     * Cost per kWh is already on AT_CHARGER basis.
     */
    @Test
    void effectiveKwhForCost_atCharger_returnsUnchanged() {
        EvLog log = atChargerLog(null, new BigDecimal("30.0"), null, ChargingType.UNKNOWN, false, T1);

        BigDecimal result = service.effectiveKwhForCost(log);

        assertEquals(new BigDecimal("30.0"), result);
    }

    /**
     * AT_VEHICLE log (Smartcar/Tesla): kwhCharged is divided by AC efficiency (0.90)
     * to get AT_CHARGER equivalent — because cost_eur was billed on grid energy, not battery energy.
     *
     *   effectiveKwhForCost = 30 / 0.90 = 33.3333 kWh
     *
     * Without this correction: avgCostPerKwh = cost / 30 kWh → appears 11% too expensive.
     * With correction:         avgCostPerKwh = cost / 33.33 kWh → reflects true grid price.
     */
    @Test
    void effectiveKwhForCost_atVehicle_dividedByAcEfficiency() {
        EvLog log = atVehicleLog(null, new BigDecimal("30.0"), null, T1);

        BigDecimal result = service.effectiveKwhForCost(log);

        // 30 / 0.90 = 33.3333...
        assertEquals(0, new BigDecimal("33.3333").compareTo(result),
                "AT_VEHICLE kWh should be upscaled to AT_CHARGER equivalent: 30 / 0.90 = 33.3333");
    }

    /**
     * AT_VEHICLE log at DC public charger: divided by DC efficiency (0.95).
     *
     *   effectiveKwhForCost = 30 / 0.95 = 31.5789 kWh
     */
    @Test
    void effectiveKwhForCost_atVehicleDcPublic_dividedByDcEfficiency() {
        // Smartcar at public DC charger — but SMARTCAR_LIVE doesn't expose charging type,
        // so we use AT_VEHICLE with UNKNOWN type + publicCharging=true to trigger DC proxy
        EvLog log = EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(new BigDecimal("30.0"))
                .costEur(new BigDecimal("10.00"))
                .chargeDurationMinutes(60)
                .geohash("u33d1")
                .maxChargingPowerKw(new BigDecimal("50.0"))
                .loggedAt(T1)
                .dataSource(DataSource.SMARTCAR_LIVE)
                .includeInStatistics(true)
                .chargingType(ChargingType.UNKNOWN)
                .isPublicCharging(true) // publicCharging=true → DC proxy
                .createdAt(T1)
                .updatedAt(T1)
                .build();

        BigDecimal result = service.effectiveKwhForCost(log);

        // 30 / 0.95 = 31.5789...
        assertEquals(0, new BigDecimal("31.5789").compareTo(result),
                "AT_VEHICLE at public charger should use DC efficiency: 30 / 0.95 = 31.5789");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private EvLog atChargerLog(Integer odometerKm, BigDecimal kwhCharged, Integer socAfter,
                                ChargingType chargingType, boolean publicCharging, LocalDateTime loggedAt) {
        return atChargerLog(odometerKm, kwhCharged, socAfter, chargingType, publicCharging, loggedAt, new BigDecimal("11.0"));
    }

    private EvLog atChargerLog(Integer odometerKm, BigDecimal kwhCharged, Integer socAfter,
                                ChargingType chargingType, boolean publicCharging, LocalDateTime loggedAt,
                                BigDecimal maxChargingPowerKw) {
        return atChargerLog(odometerKm, kwhCharged, socAfter, chargingType, publicCharging, loggedAt, maxChargingPowerKw, 60);
    }

    private EvLog atChargerLog(Integer odometerKm, BigDecimal kwhCharged, Integer socAfter,
                                ChargingType chargingType, boolean publicCharging, LocalDateTime loggedAt,
                                BigDecimal maxChargingPowerKw, Integer durationMinutes) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(kwhCharged)
                .costEur(new BigDecimal("10.00"))
                .chargeDurationMinutes(durationMinutes)
                .geohash("u33d1")
                .odometerKm(odometerKm)
                .maxChargingPowerKw(maxChargingPowerKw)
                .socAfterChargePercent(socAfter)
                .loggedAt(loggedAt)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(chargingType)
                .isPublicCharging(publicCharging)
                .createdAt(loggedAt)
                .updatedAt(loggedAt)
                .build();
    }

    private EvLog atVehicleLog(Integer odometerKm, BigDecimal kwhCharged, Integer socAfter,
                                LocalDateTime loggedAt) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(UUID.randomUUID())
                .kwhCharged(kwhCharged)
                .costEur(new BigDecimal("10.00"))
                .chargeDurationMinutes(60)
                .geohash("u33d1")
                .odometerKm(odometerKm)
                .maxChargingPowerKw(new BigDecimal("11.0"))
                .socAfterChargePercent(socAfter)
                .loggedAt(loggedAt)
                .dataSource(DataSource.SMARTCAR_LIVE)
                .includeInStatistics(true)
                .chargingType(ChargingType.UNKNOWN)
                .createdAt(loggedAt)
                .updatedAt(loggedAt)
                .build();
    }
}
