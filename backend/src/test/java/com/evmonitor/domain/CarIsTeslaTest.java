package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Brand identity helper used by the marken-aware entitlement gates
 * (live charging, live trips). Tesla cars get the free data + live-card tier,
 * other brands stay on the paid AutoSync model.
 */
class CarIsTeslaTest {

    @Test
    void teslaModels_areTesla() {
        assertTrue(carWith(CarBrand.CarModel.MODEL_3).isTesla());
        assertTrue(carWith(CarBrand.CarModel.MODEL_Y).isTesla());
    }

    @Test
    void nonTeslaModels_areNotTesla() {
        assertFalse(carWith(CarBrand.CarModel.ID_3).isTesla());
        assertFalse(carWith(CarBrand.CarModel.ID_4).isTesla());
    }

    @Test
    void nullModel_isNotTesla() {
        assertFalse(Car.builder().build().isTesla());
    }

    private Car carWith(CarBrand.CarModel model) {
        return Car.builder().model(model).build();
    }
}
