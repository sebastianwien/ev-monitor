package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.VehicleCategory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CarResponseDisplayNameTest {

    @Test
    void liefertDenFertigenAnzeigenamen() {
        Car car = Car.createNew(UUID.randomUUID(), CarBrand.CarModel.ID_5, 2023, "B-EV 1", null, null, null, null);

        CarResponse response = CarResponse.fromDomain(car);

        assertThat(response.displayName()).isEqualTo("VW ID.5");
        assertThat(response.modelDisplayName()).isEqualTo("ID.5");
        assertThat(response.brand()).isEqualTo(CarBrand.VW);
        assertThat(response.model()).isEqualTo(CarBrand.CarModel.ID_5);
    }

    @Test
    void liefertDieFahrzeugklasseFuerDenBenzinerVergleich() {
        Car car = Car.createNew(UUID.randomUUID(), CarBrand.CarModel.ID_5, 2023, "B-EV 1", null, null, null, null);

        assertThat(CarResponse.fromDomain(car).category()).isEqualTo(VehicleCategory.SUV);
    }
}
