package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks in the Volvo electric line-up and the brutto-kWh convention used as
 * lookup key in {@code battery_capacity_kwh}. The actual WLTP figures live in
 * vehicle_specification; here we only guard the enum that drives the model
 * picker and the SEO model pages.
 */
class VolvoModelsTest {

    private static List<Double> caps(CarBrand.CarModel model) {
        return model.getCapacityEntries().stream().map(CapacityEntry::kWh).toList();
    }

    @Test
    void volvoBrandExposesFullElectricLineup() {
        List<CarBrand.CarModel> volvos = CarBrand.CarModel.byBrand(CarBrand.VOLVO);
        assertTrue(volvos.containsAll(List.of(
                CarBrand.CarModel.EX_30,
                CarBrand.CarModel.EX_30_CROSS_COUNTRY,
                CarBrand.CarModel.EX_40,
                CarBrand.CarModel.EC_40,
                CarBrand.CarModel.EX_90,
                CarBrand.CarModel.ES_90)),
                "Volvo line-up is incomplete: " + volvos);
    }

    @Test
    void capacitiesUseBruttoLookupKeys() {
        assertEquals(List.of(51.0, 69.0), caps(CarBrand.CarModel.EX_30));
        assertEquals(List.of(69.0), caps(CarBrand.CarModel.EX_30_CROSS_COUNTRY));
        assertEquals(List.of(70.0, 82.0), caps(CarBrand.CarModel.EX_40));
        assertEquals(List.of(70.0, 82.0), caps(CarBrand.CarModel.EC_40));
        assertEquals(List.of(104.0, 111.0), caps(CarBrand.CarModel.EX_90));
        assertEquals(List.of(92.0, 106.0), caps(CarBrand.CarModel.ES_90));
    }

    @Test
    void variantNameResolvesForBruttoKey() {
        assertEquals("Extended Range",
                CarBrand.CarModel.EX_30.variantNameFor(BigDecimal.valueOf(69.0)).orElse(null));
        assertEquals("Twin Motor",
                CarBrand.CarModel.EX_90.variantNameFor(BigDecimal.valueOf(111.0)).orElse(null));
    }

    @Test
    void allVolvoModelsHaveCategory() {
        for (CarBrand.CarModel model : CarBrand.CarModel.byBrand(CarBrand.VOLVO)) {
            assertNotNull(model.getCategory(), model.name() + " has no category");
        }
    }
}
