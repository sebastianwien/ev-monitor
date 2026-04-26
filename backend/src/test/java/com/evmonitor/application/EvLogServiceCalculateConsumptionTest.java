package com.evmonitor.application;

import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.domain.*;
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

    @Mock private VehicleSpecificationRepository vehicleSpecificationRepository;

    private ConsumptionCalculationService service;

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
        service = new ConsumptionCalculationService(vehicleSpecificationRepository, props, mock(BatterySohRepository.class));
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
     * kWh-primary formula gives the same result as the old socBefore-primary formula
     * when socBefore is consistent with kwhCharged (no battery degradation).
     *
     *   logX: odometer=10000, socAfter=80%
     *   logY: odometer=10300, kwhCharged=52.5, socAfter=85%, socBefore=15%
     *   battery=75 kWh, distance=300 km
     *
     *   kWh-primary: 52.5 + (80-85)*75/100 = 52.5 - 3.75 = 48.75 kWh → 16.25 kWh/100km
     *
     * Same result as the old formula because socBefore=15 matches what kWh implies:
     *   85 - 52.5/75*100 = 15% — no SoH divergence, formulas are algebraically equivalent.
     */
    @Test
    void kwhPrimary_consistentSocBefore_sameResultAsOldFormula() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logYWithSocBefore(10300, new BigDecimal("52.5"), 85, 15);

        Optional<BigDecimal> result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("16.25"), result.get());
    }

    /**
     * kWh is the primary signal — stored socBefore is ignored when kwhCharged is present.
     * Charging loss correction happens via effectiveKwhForConsumption (efficiency factor),
     * not via the stored socBefore value.
     *
     * The result is purely driven by kWh + net SoC-level correction between sessions:
     *   energyConsumed = effectiveKwh + (socAfter_X - socAfter_Y) * C / 100
     *                  = 58.3 + (80 - 85) * 75/100 = 58.3 - 3.75 = 54.55 kWh
     *   consumption    = 54.55 / 300 * 100 = 18.18 kWh/100km
     *
     * Both withStored and withoutStored give the same result — socBefore no longer affects output.
     */
    @Test
    void kwhPrimary_socBeforeIgnored_resultDrivenByKwhAndSocAfterDelta() {
        EvLog logX = logX(10000, 80);
        EvLog logYWithStoredSoc    = logYWithSocBefore(10300, new BigDecimal("58.3"), 85, 15);
        EvLog logYWithoutStoredSoc = logY(10300, new BigDecimal("58.3"), 85);

        BigDecimal withStored    = service.calculateConsumption(logX, logYWithStoredSoc, BATTERY_75).orElseThrow();
        BigDecimal withoutStored = service.calculateConsumption(logX, logYWithoutStoredSoc, BATTERY_75).orElseThrow();

        assertEquals(new BigDecimal("18.18"), withStored);
        assertEquals(withStored, withoutStored, "socBefore must not influence result when kwhCharged is present");
    }

    /**
     * Battery degradation scenario: BMS reports socBefore=20% (calibrated to degraded real capacity),
     * but nominal capacity is 75 kWh. Old formula multiplied nominal C by BMS-SOC-delta → overestimated.
     * kWh-primary uses the actual measured energy instead, eliminating the SoH bias.
     *
     *   C_nominal=75 kWh, C_real≈65 kWh (~87% SoH)
     *   kwhCharged=39.0 = 60% of real 65 kWh (what actually entered the battery)
     *   socBefore=20%, socAfter=80% (BMS-reported, calibrated to real capacity)
     *
     *   Old (socBefore primary): (80-20) * 75/100 = 45.0 kWh → 15.00 kWh/100km  ← inflated by SoH mismatch
     *   New (kWh primary):       39.0 + (80-80)*75/100 = 39.0 kWh → 13.00 kWh/100km ← reflects actual energy
     */
    @Test
    void kwhPrimary_degradedBattery_notInflatedBySocNominalCapacityMismatch() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logYWithSocBefore(10300, new BigDecimal("39.0"), 80, 20);

        Optional<BigDecimal> result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("13.00"), result.get());
    }

    /**
     * Degraded battery with non-zero SoC-delta between sessions.
     *
     * This test enforces the caller contract: batteryCapacityKwh MUST be the effective
     * (SoH-adjusted) capacity, not the nominal spec capacity. The delta-correction term
     * (socAfter_X - socAfter_Y) * capacity / 100 is capacity-sensitive — passing nominal
     * inflates the correction by the same ratio as the SoH deficit.
     *
     *   C_nominal=75 kWh, C_effective=65 kWh (~87% SoH)
     *   kwhCharged=50.0, distance=300 km, logX ends at 80%, logY ends at 70%
     *
     *   Correct (effective 65 kWh):
     *     correction     = (80-70) × 65/100 = 6.5 kWh
     *     energyConsumed = 50.0 + 6.5 = 56.5 kWh → 18.83 kWh/100km
     *
     *   Wrong (nominal 75 kWh):
     *     correction     = (80-70) × 75/100 = 7.5 kWh
     *     energyConsumed = 50.0 + 7.5 = 57.5 kWh → 19.17 kWh/100km  ← inflated by SoH mismatch
     *
     * In production, calculateConsumptionPerLog receives capacity from buildCapacityLookup()
     * which returns getEffectiveBatteryCapacityKwhAt() — so the production path is correct.
     * Direct callers of calculateConsumption() must ensure they pass effective capacity.
     */
    @Test
    void kwhPrimary_degradedBattery_deltaTermInflatedWhenNominalCapacityPassedInsteadOfEffective() {
        BigDecimal nominal   = new BigDecimal("75.0");
        BigDecimal effective = new BigDecimal("65.0"); // ~87% SoH

        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10300, new BigDecimal("50.0"), 70); // 10% SoC drop between sessions

        BigDecimal withEffective = service.calculateConsumption(logX, logY, effective).orElseThrow();
        BigDecimal withNominal   = service.calculateConsumption(logX, logY, nominal).orElseThrow();

        // Correct result: delta-term uses effective 65 kWh
        assertEquals(new BigDecimal("18.83"), withEffective);

        // Wrong (inflated) result: delta-term uses nominal 75 kWh — documented as regression guard
        assertEquals(new BigDecimal("19.17"), withNominal);

        assertNotEquals(withEffective, withNominal, "delta term must be sensitive to capacity — always pass effective, not nominal");
    }

    /**
     * Positive SoC-delta correction: logX ended at 80%, logY ended at 60%.
     * Net 20% more battery was used than what kWh directly accounts for.
     * Correction = (80-60) * 75/100 = 15 kWh added to measured kWh.
     *
     *   energyConsumed = 25.0 + 15.0 = 40.0 kWh → 40.0/300*100 = 13.33 kWh/100km
     */
    @Test
    void kwhPrimary_positiveSocDeltaCorrection_whenEndedAtLowerChargeLevel() {
        EvLog logX = logX(10000, 80);
        EvLog logY = logY(10300, new BigDecimal("25.0"), 60);

        Optional<BigDecimal> result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("13.33"), result.get());
    }

    /**
     * Negative SoC-delta correction: logX ended at 60%, logY ended at 80%.
     * Net 20% more was put into battery than what was consumed — the extra kWh is subtracted.
     * Correction = (60-80) * 75/100 = -15 kWh subtracted from measured kWh.
     *
     *   energyConsumed = 30.0 - 15.0 = 15.0 kWh → 15.0/300*100 = 5.00 kWh/100km
     */
    @Test
    void kwhPrimary_negativeSocDeltaCorrection_whenEndedAtHigherChargeLevel() {
        EvLog logX = logX(10000, 60);
        EvLog logY = logY(10300, new BigDecimal("30.0"), 80);

        Optional<BigDecimal> result = service.calculateConsumption(logX, logY, BATTERY_75);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5.00"), result.get());
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
                .socAfterChargePercent(socAfterChargePercent != null ? new BigDecimal(socAfterChargePercent) : null)
                .socBeforeChargePercent(socBeforeChargePercent != null ? new BigDecimal(socBeforeChargePercent) : null)
                .loggedAt(loggedAt)
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(ChargingType.UNKNOWN)
                .createdAt(loggedAt)
                .updatedAt(loggedAt)
                .build();
    }
}
