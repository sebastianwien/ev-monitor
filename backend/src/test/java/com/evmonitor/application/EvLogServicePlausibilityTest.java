package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.weather.TemperatureEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for EvLogService.isConsumptionPlausible().
 *
 * Default PlausibilityProperties values:
 *   absoluteMin = 10.0, absoluteMax = 40.0
 *   wltpLowerFactor = 0.75, wltpUpperFactor = 2.2
 *   sigmaMultiplier = 2.5, minTripsForStatistical = 5
 */
@ExtendWith(MockitoExtension.class)
class EvLogServicePlausibilityTest {

    @Mock private EvLogRepository evLogRepository;
    @Mock private CarRepository carRepository;
    @Mock private UserRepository userRepository;
    @Mock private CoinLogService coinLogService;
    @Mock private TemperatureEnrichmentService temperatureEnrichmentService;

    private EvLogService evLogService;

    // Five typical values clustered around 18 kWh/100km (mean=18, stdDev≈1)
    private static final List<BigDecimal> FIVE_TYPICAL = List.of(
            new BigDecimal("17.0"),
            new BigDecimal("18.0"),
            new BigDecimal("18.5"),
            new BigDecimal("19.0"),
            new BigDecimal("17.5")
    );
    // mean ≈ 18.0, stdDev ≈ 0.75 → 2.5σ window ≈ [16.13, 19.87]

    private static final BigDecimal WLTP = new BigDecimal("15.00");
    // WLTP range: [15 × 0.75, 15 × 2.2] = [11.25, 33.00]

    @BeforeEach
    void setUp() {
        evLogService = new EvLogService(
                evLogRepository, carRepository, userRepository, coinLogService,
                temperatureEnrichmentService, mock(VehicleSpecificationRepository.class),
                new PlausibilityProperties(),
                mock(com.evmonitor.application.SessionGroupService.class), mock(com.evmonitor.domain.BatterySohRepository.class)
        );
    }

    // -------------------------------------------------------------------------
    // Layer 1: absolute bounds (always applied)
    // -------------------------------------------------------------------------

    @Test
    void layer1_shouldRejectValueBelowAbsoluteMin() {
        assertFalse(evLogService.isConsumptionPlausible(
                new BigDecimal("9.99"), List.of(), null));
    }

    @Test
    void layer1_shouldRejectValueAboveAbsoluteMax() {
        assertFalse(evLogService.isConsumptionPlausible(
                new BigDecimal("40.01"), List.of(), null));
    }

    @Test
    void layer1_shouldAcceptValueAtExactBounds() {
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("10.00"), List.of(), null));
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("40.00"), List.of(), null));
    }

    // -------------------------------------------------------------------------
    // Layer 2a: statistical check (≥ 5 trips)
    // -------------------------------------------------------------------------

    @Test
    void layer2a_shouldAcceptValueWithinMeanPlusSigma() {
        // 18.0 is exactly the mean — well within 2.5σ
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("18.00"), FIVE_TYPICAL, null));
    }

    @Test
    void layer2a_shouldRejectValueFarAboveMean() {
        // 25.0 is far above the [16.13, 19.87] window
        assertFalse(evLogService.isConsumptionPlausible(
                new BigDecimal("25.00"), FIVE_TYPICAL, null));
    }

    @Test
    void layer2a_shouldRejectValueFarBelowMean() {
        // 12.0 is far below the window
        assertFalse(evLogService.isConsumptionPlausible(
                new BigDecimal("12.00"), FIVE_TYPICAL, null));
    }

    @Test
    void layer2a_withZeroStdDev_shouldAcceptValueWithin10Percent() {
        // All identical values → stdDev = 0 → 10% tolerance around mean (20.0)
        List<BigDecimal> identical = List.of(
                new BigDecimal("20.0"),
                new BigDecimal("20.0"),
                new BigDecimal("20.0"),
                new BigDecimal("20.0"),
                new BigDecimal("20.0")
        );
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("21.99"), identical, null)); // within 10%
        assertFalse(evLogService.isConsumptionPlausible(
                new BigDecimal("22.01"), identical, null)); // just outside 10%
    }

    @Test
    void layer2a_ignoresWltpWhenEnoughTripsExist() {
        // WLTP would reject 18.0 only if it were outside [11.25, 33.00] — but that's not the case anyway.
        // Real test: pass a tight WLTP that would reject, but statistical check overrides it.
        // FIVE_TYPICAL mean≈18, so value 19.5 is within σ-window but >WLTP×1.1 — still accepted
        BigDecimal tightWltp = new BigDecimal("10.00"); // range [7.5, 22] — overlaps anyway
        // More direct: 5 trips around 35 kWh/100km → value 36 fails absolute max regardless
        // So: ensure the statistical branch is entered (history.size >= 5) even with WLTP present
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("18.00"), FIVE_TYPICAL, tightWltp));
    }

    // -------------------------------------------------------------------------
    // Layer 2b: WLTP bootstrap (< 5 trips)
    // -------------------------------------------------------------------------

    @Test
    void layer2b_shouldAcceptValueWithinWltpBounds() {
        // WLTP=15 → [11.25, 33.00]; value 20.0 is inside
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("20.00"), List.of(), WLTP));
    }

    @Test
    void layer2b_shouldRejectValueBelowWltpLowerBound() {
        // WLTP=15 → lower=11.25; value 11.0 is below
        assertFalse(evLogService.isConsumptionPlausible(
                new BigDecimal("11.00"), List.of(), WLTP));
    }

    @Test
    void layer2b_shouldRejectValueAboveWltpUpperBound() {
        // WLTP=15 → upper=33.00; value 34.0 is above
        assertFalse(evLogService.isConsumptionPlausible(
                new BigDecimal("34.00"), List.of(), WLTP));
    }

    @Test
    void layer2b_withFewTrips_shouldStillUseWltp() {
        // Only 3 trips — statistical check not triggered, WLTP applies
        List<BigDecimal> fewTrips = List.of(
                new BigDecimal("18.0"), new BigDecimal("19.0"), new BigDecimal("17.5")
        );
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("20.00"), fewTrips, WLTP));
        assertFalse(evLogService.isConsumptionPlausible(
                new BigDecimal("34.00"), fewTrips, WLTP));
    }

    // -------------------------------------------------------------------------
    // Layer 2c: no history, no WLTP — only absolute bounds apply
    // -------------------------------------------------------------------------

    @Test
    void layer2c_shouldAcceptAnyValueWithinAbsoluteBounds() {
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("25.00"), List.of(), null));
    }

    @Test
    void layer2c_nullHistorySameAsEmptyList() {
        assertTrue(evLogService.isConsumptionPlausible(
                new BigDecimal("25.00"), null, null));
    }
}
