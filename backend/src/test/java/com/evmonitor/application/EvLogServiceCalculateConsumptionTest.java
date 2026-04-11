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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for EvLogService.calculateConsumption(logX, logY, batteryCapacityKwh).
 *
 * Formula recap:
 *   socBefore(logY) = stored value if available, else socAfter(logY) - kwhCharged(logY) / battery * 100
 *   energyConsumed  = (socAfter(logX) - socBefore(logY)) * battery / 100
 *   consumption     = energyConsumed * 100 / distanceKm  [kWh/100km]
 */
@ExtendWith(MockitoExtension.class)
class EvLogServiceCalculateConsumptionTest {

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
        // Neutral efficiency factors — these tests verify formula math, not charging loss correction.
        // See EvLogServiceChargingEfficiencyTest for dedicated correction tests.
        PlausibilityProperties props = new PlausibilityProperties();
        props.setAcChargingEfficiency(1.0);
        props.setDcChargingEfficiency(1.0);
        service = new EvLogService(
                evLogRepository, carRepository, userRepository,
                coinLogService, temperatureEnrichmentService,
                vehicleSpecificationRepository, props,
                mock(com.evmonitor.domain.BatterySohRepository.class));
    }

    // -------------------------------------------------------------------------
    // Happy path — correct math
    // -------------------------------------------------------------------------

    /**
     * Manual verification:
     *   logX: odometer=10000, socAfter=80%
     *   logY: odometer=10300, kwhCharged=52.5, socAfter=85%
     *   battery=75 kWh, distance=300 km
     *
     *   socBefore(logY) = 85 - (52.5/75*100) = 85 - 70 = 15%
     *   energyConsumed  = (80 - 15) * 75 / 100 = 65 * 0.75 = 48.75 kWh
     *   consumption     = 48.75 * 100 / 300 = 16.25 kWh/100km
     */
    @Test
    void happyPath_correctMath() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10300, new BigDecimal("52.5"), 85);

        Optional<BigDecimal> result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("16.25"), result.get());
    }

    /**
     * Partial charging scenario — the case where a simple kWh-sum formula fails.
     *
     *   logX: odometer=15000, socAfter=80%
     *   logY: odometer=15150, kwhCharged=30, socAfter=90%
     *   battery=75 kWh, distance=150 km
     *
     *   socBefore(logY) = 90 - (30/75*100) = 90 - 40 = 50%
     *   energyConsumed  = (80 - 50) * 75 / 100 = 30 * 0.75 = 22.5 kWh
     *   consumption     = 22.5 * 100 / 150 = 15.00 kWh/100km
     */
    @Test
    void partialCharging_correctMath() {
        EvLog logX = logX(15000, 80);
        EvLog logY = logY(15150, new BigDecimal("30.0"), 90);

        Optional<BigDecimal> result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("15.00"), result.get());
    }

    /**
     * When socBeforeChargePercent is stored on logY, it must be used directly
     * instead of deriving it from kwhCharged (which includes charging losses).
     *
     *   logX: odometer=10000, socAfter=80%
     *   logY: odometer=10300, kwhCharged=52.5, socAfter=85%, socBefore=15%
     *   battery=75 kWh, distance=300 km
     *
     *   socBefore(logY) = 15% (stored — used directly, kwhCharged ignored for this step)
     *   energyConsumed  = (80 - 15) * 75 / 100 = 48.75 kWh
     *   consumption     = 48.75 * 100 / 300 = 16.25 kWh/100km
     *
     * Note: result is identical here because the stored soc_before matches what the formula
     * would compute — the point is to verify that the stored value takes priority.
     * A follow-up test verifies divergence when kwhCharged is inflated by losses.
     */
    @Test
    void storedSocBefore_usedDirectly_notDerivedFromKwh() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logYWithSocBefore(10300, new BigDecimal("52.5"), 85, 15);

        Optional<BigDecimal> result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("16.25"), result.get());
    }

    /**
     * When kwhCharged is inflated (e.g. grid measurement with 10% charging losses),
     * the derived socBefore would be too low and the consumption too high.
     * If socBeforeChargePercent is stored, it corrects this.
     *
     *   logX: odometer=10000, socAfter=80%
     *   logY: odometer=10300, kwhCharged=58.3 (52.5 net + ~11% loss), socAfter=85%, socBefore=15%
     *   battery=75 kWh, distance=300 km
     *
     *   Without stored socBefore: socBefore = 85 - (58.3/75*100) = 85 - 77.7 = 7.3% → overestimates consumption
     *   With stored socBefore=15%: energyConsumed = (80-15)*75/100 = 48.75 kWh → 16.25 kWh/100km
     */
    @Test
    void storedSocBefore_correctsChargingLossBias() {
        EvLog logX = logX(10000, 80);
        // kwhCharged is inflated by ~11% charging losses
        EvLog logYWithStoredSoc   = logYWithSocBefore(10300, new BigDecimal("58.3"), 85, 15);
        EvLog logYWithoutStoredSoc = logY(10300, new BigDecimal("58.3"), 85);

        BigDecimal withStored   = service.calculateConsumption(logX, logYWithStoredSoc, BATTERY_75).orElseThrow();
        BigDecimal withoutStored = service.calculateConsumption(logX, logYWithoutStoredSoc, BATTERY_75).orElseThrow();

        assertEquals(new BigDecimal("16.25"), withStored);
        assertTrue(withoutStored.compareTo(withStored) > 0, "Without stored socBefore, consumption is overestimated due to charging losses");
    }

    // -------------------------------------------------------------------------
    // logX validation
    // -------------------------------------------------------------------------

    @Test
    void logX_nullOdometer_returnsEmpty() {
        EvLog logX = logXNoOdometer(80);
        EvLog logY = logY(10300, new BigDecimal("52.5"), 85);

        assertTrue(service.calculateConsumption(logX, logY, BATTERY_75).isEmpty());
    }

    @Test
    void logX_nullSoc_returnsEmpty() {
        EvLog logX = logXNoSoc(10000);
        EvLog logY = logY(10300, new BigDecimal("52.5"), 85);

        assertTrue(service.calculateConsumption(logX, logY, BATTERY_75).isEmpty());
    }

    // -------------------------------------------------------------------------
    // logY validation
    // -------------------------------------------------------------------------

    @Test
    void logY_nullOdometer_returnsEmpty() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logYNoOdometer(new BigDecimal("52.5"), 85);

        assertTrue(service.calculateConsumption(logX, logY, BATTERY_75).isEmpty());
    }

    @Test
    void logY_nullKwhCharged_returnsEmpty() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logYNoKwh(10300, 85);

        assertTrue(service.calculateConsumption(logX, logY, BATTERY_75).isEmpty());
    }

    @Test
    void logY_nullSoc_returnsEmpty() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logYNoSoc(10300, new BigDecimal("52.5"));

        assertTrue(service.calculateConsumption(logX, logY, BATTERY_75).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Battery capacity validation
    // -------------------------------------------------------------------------

    @Test
    void nullBatteryCapacity_returnsEmpty() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10300, new BigDecimal("52.5"), 85);

        assertTrue(service.calculateConsumption(logX, logY, null).isEmpty());
    }

    @Test
    void zeroBatteryCapacity_returnsEmpty() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10300, new BigDecimal("52.5"), 85);

        assertTrue(service.calculateConsumption(logX, logY, BigDecimal.ZERO).isEmpty());
    }

    @Test
    void negativeBatteryCapacity_returnsEmpty() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10300, new BigDecimal("52.5"), 85);

        assertTrue(service.calculateConsumption(logX, logY, new BigDecimal("-75")).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Distance validation
    // -------------------------------------------------------------------------

    @Test
    void zeroDistance_returnsEmpty() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10000, new BigDecimal("52.5"), 85); // same odometer

        assertTrue(service.calculateConsumption(logX, logY, BATTERY_75).isEmpty());
    }

    @Test
    void negativeDistance_returnsEmpty() {
        EvLog logX = logX(10300, 80); // higher odometer than logY
        EvLog logY = logY(10000, new BigDecimal("52.5"), 85);

        assertTrue(service.calculateConsumption(logX, logY, BATTERY_75).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Energy consumed <= 0 (e.g. charged more than consumed, SoC increased)
    // -------------------------------------------------------------------------

    /**
     * If socAfter(logX)=20% and socBefore(logY) computes to 80%, energyConsumed
     * would be negative — physically impossible, data gap likely. Expect empty.
     *
     *   logX: socAfter=20%
     *   logY: kwhCharged=60, socAfter=80% → socBefore = 80 - (60/75*100) = 80 - 80 = 0%
     *   energyConsumed = (20 - 0) * 75/100 = 15 kWh → actually positive, let me recalculate
     *
     * For truly negative energy: make logX socAfter very low and logY charged a lot
     *   logY: kwhCharged=70, socAfter=80% → socBefore = 80 - (70/75*100) = 80 - 93.3 = -13.3%
     *   energyConsumed = (20 - (-13.3)) * 75/100 = positive — still positive
     *
     * The only way to get negative energy consumed:
     *   socAfter(logX) < socBefore(logY)
     *   i.e., logX left tank at 10%, logY arrived already at 50% (without charging)
     *   → indicates a missing log between logX and logY
     *
     *   logX: socAfter=10%
     *   logY: kwhCharged=5, socAfter=80% → socBefore = 80 - (5/75*100) = 80 - 6.67 = 73.33%
     *   energyConsumed = (10 - 73.33) * 75/100 = -47.5 kWh → negative → empty
     */
    @Test
    void negativeEnergyConsumed_returnsEmpty() {
        EvLog logX = logX(10000, 10);  // logX SoC after: 10%
        EvLog logY = logY(10300,
                new BigDecimal("5.0"),   // only 5 kWh charged
                80);                     // socAfter=80% → socBefore=73.3% → energy = (10-73.3)*75/100 < 0

        assertTrue(service.calculateConsumption(logX, logY, BATTERY_75).isEmpty());
    }

    // -------------------------------------------------------------------------
    // calculateConsumptionPerLog — minTripDistanceKm filter
    // -------------------------------------------------------------------------

    /**
     * Trips shorter than minTripDistanceKm (default 20km) must be silently excluded.
     * They produce unreliable odometer deltas and would skew the consumption distribution.
     */
    @Test
    void perLog_tripBelowMinDistance_isExcluded() {
        // 15km trip — below the 20km minimum
        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10015, new BigDecimal("3.0"), 85); // 15km

        Map<UUID, ConsumptionResult> result =
                service.calculateConsumptionPerLog(List.of(logX, logY), BATTERY_75, null);

        assertTrue(result.isEmpty(), "Trip < minTripDistanceKm should be excluded");
    }

    @Test
    void perLog_tripAtExactMinDistance_isIncluded() {
        // 20km trip — exactly at the minimum (boundary: included)
        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10020, new BigDecimal("4.0"), 85); // 20km

        Map<UUID, ConsumptionResult> result =
                service.calculateConsumptionPerLog(List.of(logX, logY), BATTERY_75, null);

        assertFalse(result.isEmpty(), "Trip == minTripDistanceKm should be included");
    }

    @Test
    void perLog_shortTripExcluded_longTripIncluded() {
        // Three logs: logX → logY1 (10km, too short) → logY2 (300km, ok)
        // logY1's direct predecessor is logX → short trip, excluded
        // logY2's direct predecessor is logY1 → canBeUsedAsLogX? yes (has odometer + soc) → trip ok
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 2, 10, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 1, 3, 10, 0);

        EvLog logX  = evLog(UUID.randomUUID(), 10000, null,                   80, null, t1);
        EvLog logY1 = evLog(UUID.randomUUID(), 10010, new BigDecimal("2.0"),  85, null, t2); // 10km
        EvLog logY2 = evLog(UUID.randomUUID(), 10310, new BigDecimal("50.0"), 90, null, t3); // 300km from logY1

        Map<UUID, ConsumptionResult> result =
                service.calculateConsumptionPerLog(List.of(logX, logY1, logY2), BATTERY_75, null);

        assertFalse(result.containsKey(logY1.getId()), "10km trip should be excluded");
        assertTrue(result.containsKey(logY2.getId()),  "300km trip should be included");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** logX with odometer and SoC — valid as logX, no kwhCharged needed. */
    private EvLog logX(int odometerKm, int socAfterPercent) {
        return evLog(UUID.randomUUID(), odometerKm, null, socAfterPercent, null, T1);
    }

    private EvLog logXNoOdometer(int socAfterPercent) {
        return evLog(UUID.randomUUID(), null, null, socAfterPercent, null, T1);
    }

    private EvLog logXNoSoc(int odometerKm) {
        return evLog(UUID.randomUUID(), odometerKm, null, null, null, T1);
    }

    /** logY with odometer, kwhCharged, and SoC — must be complete. */
    private EvLog logY(int odometerKm, BigDecimal kwhCharged, int socAfterPercent) {
        return evLog(UUID.randomUUID(), odometerKm, kwhCharged, socAfterPercent, null, T2);
    }

    /** logY with stored socBeforeChargePercent — bypasses kwhCharged-based derivation. */
    private EvLog logYWithSocBefore(int odometerKm, BigDecimal kwhCharged, int socAfterPercent, int socBeforePercent) {
        return evLog(UUID.randomUUID(), odometerKm, kwhCharged, socAfterPercent, socBeforePercent, T2);
    }

    private EvLog logYNoOdometer(BigDecimal kwhCharged, int socAfterPercent) {
        return evLog(UUID.randomUUID(), null, kwhCharged, socAfterPercent, null, T2);
    }

    private EvLog logYNoKwh(int odometerKm, int socAfterPercent) {
        return evLog(UUID.randomUUID(), odometerKm, null, socAfterPercent, null, T2);
    }

    private EvLog logYNoSoc(int odometerKm, BigDecimal kwhCharged) {
        return evLog(UUID.randomUUID(), odometerKm, kwhCharged, null, null, T2);
    }

    private EvLog evLog(UUID id, Integer odometerKm, BigDecimal kwhCharged,
                        Integer socAfterChargePercent, Integer socBeforeChargePercent, LocalDateTime loggedAt) {
        return EvLog.builder()
                .id(id)
                .carId(UUID.randomUUID())
                .kwhCharged(kwhCharged)
                .costEur(new BigDecimal("10.00"))
                .chargeDurationMinutes(60)
                .geohash("u33d1")
                .odometerKm(odometerKm)
                .maxChargingPowerKw(new BigDecimal("11.0"))
                .socAfterChargePercent(socAfterChargePercent)
                .socBeforeChargePercent(socBeforeChargePercent)
                .loggedAt(loggedAt)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(ChargingType.UNKNOWN)
                .createdAt(loggedAt)
                .updatedAt(loggedAt)
                .build();
    }
}
