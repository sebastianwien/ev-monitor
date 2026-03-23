package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VehicleCategory and CarModel.byCategory().
 */
class VehicleCategoryTest {

    @Test
    void allCarModelsHaveCategory() {
        for (CarBrand.CarModel model : CarBrand.CarModel.values()) {
            assertNotNull(model.getCategory(),
                    model.name() + " has no category assigned");
        }
    }

    @Test
    void byCategoryReturnsSuvModels() {
        List<CarBrand.CarModel> suvs = CarBrand.CarModel.byCategory(VehicleCategory.SUV);
        assertFalse(suvs.isEmpty(), "SUV category should not be empty");
        assertTrue(suvs.contains(CarBrand.CarModel.MODEL_Y), "Model Y should be SUV");
        assertTrue(suvs.contains(CarBrand.CarModel.ID_4), "ID.4 should be SUV");
        assertTrue(suvs.contains(CarBrand.CarModel.IONIQ_5), "Ioniq 5 should be SUV");
    }

    @Test
    void byCategoryReturnsCityCarModels() {
        List<CarBrand.CarModel> cityCars = CarBrand.CarModel.byCategory(VehicleCategory.CITY_CAR);
        assertFalse(cityCars.isEmpty(), "CITY_CAR category should not be empty");
        assertTrue(cityCars.contains(CarBrand.CarModel.E_UP), "e-up! should be CITY_CAR");
        assertTrue(cityCars.contains(CarBrand.CarModel.AMI), "Ami should be CITY_CAR");
        assertTrue(cityCars.contains(CarBrand.CarModel.DACIA_SPRING), "Dacia Spring should be CITY_CAR");
    }

    @Test
    void byCategoryReturnsPickupModels() {
        List<CarBrand.CarModel> pickups = CarBrand.CarModel.byCategory(VehicleCategory.PICKUP);
        assertFalse(pickups.isEmpty(), "PICKUP category should not be empty");
        assertTrue(pickups.contains(CarBrand.CarModel.CYBERTRUCK), "Cybertruck should be PICKUP");
        assertTrue(pickups.contains(CarBrand.CarModel.F_150_LIGHTNING), "F-150 Lightning should be PICKUP");
        assertTrue(pickups.contains(CarBrand.CarModel.R1T), "R1T should be PICKUP");
    }

    @Test
    void modelSReturnsLuxuryCategory() {
        assertEquals(VehicleCategory.LUXURY, CarBrand.CarModel.MODEL_S.getCategory());
    }

    @Test
    void taycaReturnssSportsCategory() {
        assertEquals(VehicleCategory.SPORTS, CarBrand.CarModel.TAYCAN.getCategory());
    }

    @Test
    void allNineCategorieshaveAtLeastOneModel() {
        for (VehicleCategory cat : VehicleCategory.values()) {
            List<CarBrand.CarModel> models = CarBrand.CarModel.byCategory(cat);
            assertFalse(models.isEmpty(), "Category " + cat + " has no models assigned");
        }
    }
}
