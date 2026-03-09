package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EvLogService.lookupWltp().
 *
 * Verifies that the DB query works correctly with the exact parameter format
 * that lookupWltp() derives from a Car object:
 *   - carBrand  = car.getModel().getBrand().name()   e.g. "TESLA"
 *   - carModel  = car.getModel().name()              e.g. "MODEL_3"
 *   - capacity  = car.getBatteryCapacityKwh()        BigDecimal scale sensitivity
 *   - wltpType  = WltpType.COMBINED
 *
 * Each test uses a unique battery capacity to avoid unique-constraint conflicts
 * with seed data or other tests in the shared test DB.
 */
class EvLogServiceLookupWltpTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    private static final BigDecimal WLTP_CONSUMPTION = new BigDecimal("14.90");
    private static final BigDecimal WLTP_RANGE       = new BigDecimal("560.0");

    /**
     * Returns a capacity that is unique per test invocation and never in seed data.
     * Uses the range 900.00–900.99 with exactly 2 decimal places to match the
     * column definition: battery_capacity_kwh NUMERIC(10,2).
     * Values > 2 decimal places would be rounded by Postgres on insert, causing
     * the subsequent query to not find the row.
     *
     * Uses a time-seeded AtomicLong so values are unique across test methods
     * within a run AND across repeated runs against the same shared test DB.
     */
    private static final java.util.concurrent.atomic.AtomicLong SEQ =
            new java.util.concurrent.atomic.AtomicLong(
                    Math.abs(System.currentTimeMillis() % 10_000_000L));

    private BigDecimal uniqueCapacity() {
        return new BigDecimal(SEQ.getAndIncrement() + ".00");
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void shouldFindWltp_whenSpecExistsWithMatchingEnumNames() {
        BigDecimal capacity = uniqueCapacity();
        // Spec saved with enum-name strings — exactly as lookupWltp() will query
        saveSpec("TESLA", "MODEL_3", capacity, WLTP_CONSUMPTION);

        Car car = carWithCapacity(CarBrand.CarModel.MODEL_3, capacity);

        BigDecimal result = evLogService.lookupWltp(car);

        assertNotNull(result, "Should find WLTP when brand/model/capacity match");
        assertEquals(WLTP_CONSUMPTION, result);
    }

    @Test
    void shouldFindWltp_whenCapacityScaleDiffers() {
        // Saved with scale 1 (900.x0), queried with scale 2 (900.x00) — verifies
        // that Postgres NUMERIC comparison is scale-insensitive
        BigDecimal savedCapacity   = uniqueCapacity();                              // e.g. 900.42
        BigDecimal queriedCapacity = savedCapacity.setScale(savedCapacity.scale() + 1); // 900.420

        saveSpec("TESLA", "MODEL_3", savedCapacity, WLTP_CONSUMPTION);

        Car car = carWithCapacity(CarBrand.CarModel.MODEL_3, queriedCapacity);

        BigDecimal result = evLogService.lookupWltp(car);

        assertNotNull(result, "BigDecimal scale difference should not prevent match");
        assertEquals(WLTP_CONSUMPTION, result);
    }

    // -------------------------------------------------------------------------
    // No match — returns null
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnNull_whenNoSpecExists() {
        // No spec saved for this capacity — MODEL_Y with unique capacity
        Car car = carWithCapacity(CarBrand.CarModel.MODEL_Y, uniqueCapacity());

        assertNull(evLogService.lookupWltp(car));
    }

    @Test
    void shouldReturnNull_whenCapacityDoesNotMatch() {
        BigDecimal capacity = uniqueCapacity();
        saveSpec("TESLA", "MODEL_3", capacity, WLTP_CONSUMPTION);

        Car car = carWithCapacity(CarBrand.CarModel.MODEL_3, capacity.add(new BigDecimal("5.0")));

        assertNull(evLogService.lookupWltp(car), "Different capacity should not match");
    }

    @Test
    void shouldReturnNull_whenCarHasNullBatteryCapacity() {
        Car car = carWithCapacity(CarBrand.CarModel.MODEL_3, null);

        assertNull(evLogService.lookupWltp(car));
    }

    // -------------------------------------------------------------------------
    // Correct brand/model string format
    // -------------------------------------------------------------------------

    @Test
    void shouldNotMatch_whenSpecStoredWithDisplayNameInsteadOfEnumName() {
        // Guard: if someone accidentally stores display names ("Tesla", "Model 3")
        // instead of enum names ("TESLA", "MODEL_3"), lookupWltp() must NOT match.
        // Uses unique capacity so no existing seed data row can interfere.
        BigDecimal capacity = uniqueCapacity();
        saveSpec("Tesla", "Model 3", capacity, WLTP_CONSUMPTION);

        Car car = carWithCapacity(CarBrand.CarModel.MODEL_3, capacity);

        assertNull(evLogService.lookupWltp(car),
                "Display names ('Tesla', 'Model 3') must not match enum names ('TESLA', 'MODEL_3')");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void saveSpec(String brand, String model, BigDecimal capacity, BigDecimal wltpConsumption) {
        vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                brand, model, capacity, WLTP_RANGE, wltpConsumption,
                VehicleSpecification.WltpType.COMBINED));
    }

    private Car carWithCapacity(CarBrand.CarModel model, BigDecimal batteryCapacityKwh) {
        LocalDateTime now = LocalDateTime.now();
        return new Car(
                UUID.randomUUID(),
                UUID.randomUUID(),
                model,
                2023,
                "TEST-1",
                "Long Range",
                batteryCapacityKwh,
                new BigDecimal("280.0"),
                null, null,
                CarStatus.ACTIVE,
                now, now,
                null, false, false
        );
    }
}
