package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live-trip-detection eligibility per car model. The matrix is hand-curated based on
 * raw-data analysis of {@code smartcar_webhook_raw_log} (and Tesla telemetry quality).
 * Models not in the whitelist still get their trip records persisted in the DB - they
 * just stay hidden from the regular user UI so we can re-evaluate data quality later.
 */
class TripDetectionEligibilityTest {

    @Test
    void teslaModels_areEligible() {
        // Tesla data comes from Fleet Telemetry, not Smartcar - quality is consistently
        // good. All Tesla models stay eligible.
        assertTrue(TripDetectionEligibility.isEligible(CarBrand.CarModel.MODEL_3));
        assertTrue(TripDetectionEligibility.isEligible(CarBrand.CarModel.MODEL_Y));
        assertTrue(TripDetectionEligibility.isEligible(CarBrand.CarModel.MODEL_S));
        assertTrue(TripDetectionEligibility.isEligible(CarBrand.CarModel.MODEL_X));
    }

    @Test
    void polestar2And4_areEligible() {
        // Polestar 2: decimal odometer (~91%), 6 min cadence - good enough.
        // Polestar 4: integer odometer but decimal SoC (~65%) + tight 6 min cadence.
        assertTrue(TripDetectionEligibility.isEligible(CarBrand.CarModel.POLESTAR_2));
        assertTrue(TripDetectionEligibility.isEligible(CarBrand.CarModel.POLESTAR_4));
    }

    @Test
    void polestar3_isNotEligible() {
        // Polestar 3: 0% decimal odometer, 0% decimal SoC, 9.5 min cadence.
        // Smartcar/OEM data too coarse for reliable detection. Keep collecting,
        // re-evaluate if OEM tightens precision.
        assertFalse(TripDetectionEligibility.isEligible(CarBrand.CarModel.POLESTAR_3));
    }

    @Test
    void unknownModels_areNotEligible() {
        // Default = not eligible (data is collected, but not shown). Brands not in the
        // matrix can be promoted once we've audited a sample of their trip data.
        assertFalse(TripDetectionEligibility.isEligible(CarBrand.CarModel.ID_4));
        assertFalse(TripDetectionEligibility.isEligible(CarBrand.CarModel.ENYAQ));
    }

    @Test
    void nullModel_isNotEligible() {
        assertFalse(TripDetectionEligibility.isEligible((CarBrand.CarModel) null));
    }
}
