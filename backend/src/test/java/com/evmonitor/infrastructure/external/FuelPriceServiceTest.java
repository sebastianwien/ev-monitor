package com.evmonitor.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FuelPriceServiceTest {

    @Test
    void withoutApiKey_refreshKeepsFallbackPrices() {
        FuelPriceService service = new FuelPriceService(new ObjectMapper());

        service.refresh();

        assertThat(service.getBenzinPrice()).isEqualTo(2.15);
        assertThat(service.getDieselPrice()).isEqualTo(2.32);
        assertThat(service.getAvgFuelPrice()).isEqualTo((2.15 + 2.32) / 2.0);
    }

    @Test
    void combustionConsumptionIsOneSharedConstant() {
        assertThat(FuelPriceService.COMBUSTION_LITERS_PER_100_KM).isEqualByComparingTo("7.0");
    }
}
