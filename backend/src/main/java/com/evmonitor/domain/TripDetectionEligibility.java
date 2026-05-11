package com.evmonitor.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Whitelist of car models for which live-trip-detection delivers reliable enough data
 * to surface in the regular user UI. Models outside this whitelist still get their
 * trip records persisted in the DB - they're just hidden from the read-path (except
 * for ADMIN / BETA_TESTER, who keep audit access).
 *
 * <p>Why hide instead of stop collecting: a future Smartcar/OEM precision improvement
 * (or a manufacturer-side fix) can flip a model to eligible retroactively, and the
 * collected history becomes immediately usable - no backfill needed.
 *
 * <h3>Curation criteria (as of 2026-05-11)</h3>
 * <ul>
 *   <li>Tesla (all models): Fleet Telemetry, 5s cadence, decimal SoC + odometer - reliable.</li>
 *   <li>Polestar 2 (2022 / 2024): ~91% decimal odometer, 6 min driving cadence - reliable.</li>
 *   <li>Polestar 4: integer odometer (1 km resolution) but decimal SoC (~65%) and
 *       6 min cadence - reliable with caveat that route-distance precision is coarser.</li>
 *   <li>Polestar 3: 0% decimal odometer, 0% decimal SoC, 9.5 min cadence -
 *       Smartcar/OEM data too coarse, blocked until OEM tightens precision.</li>
 *   <li>VW / Skoda / Audi / others via Smartcar: integer-only data, 10-75 min gaps,
 *       single-event distances up to 239 km - blocked, future-eval-when-needed.</li>
 * </ul>
 */
public final class TripDetectionEligibility {

    private TripDetectionEligibility() {}

    private static final Set<CarBrand.CarModel> ELIGIBLE_MODELS = EnumSet.of(
            // Tesla - Fleet Telemetry
            CarBrand.CarModel.MODEL_3,
            CarBrand.CarModel.MODEL_Y,
            CarBrand.CarModel.MODEL_S,
            CarBrand.CarModel.MODEL_X,
            // Polestar via Smartcar
            CarBrand.CarModel.POLESTAR_2,
            CarBrand.CarModel.POLESTAR_4
            // Polestar 3 and all other brands: collected but hidden
    );

    public static boolean isEligible(CarBrand.CarModel model) {
        return model != null && ELIGIBLE_MODELS.contains(model);
    }

    public static boolean isEligible(Car car) {
        return car != null && isEligible(car.getModel());
    }
}
