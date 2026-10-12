package com.evmonitor.application;

import com.evmonitor.application.charging.FastChargePower;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicModelServiceFastChargePowerTest {

    private final JpaEvLogRepository evLogRepository = mock(JpaEvLogRepository.class);
    private final PublicModelService service = new PublicModelService(evLogRepository,
            mock(JpaVehicleSpecificationRepository.class), mock(JpaUserRepository.class), mock(CarRepository.class),
            mock(EvTripRepository.class), mock(EvLogStatisticsService.class));

    @Test
    void thePlaceholderModelForUnlistedCarsNeverEntersTheFleetMedian() {
        // SONSTIGE_CUSTOM mixes arbitrary cars: like the ranking, the table leaves it out.
        // Without it the median of 85 and 112 is 98.5; with its 56 kW it would be 85.
        when(evLogRepository.findFastChargePowerPerModel(false)).thenReturn(List.of(
                new Object[]{"ID_3", new BigDecimal("85"), 300L},
                new Object[]{"MODEL_3", 112.0, 120L},
                new Object[]{"SONSTIGE_CUSTOM", new BigDecimal("56"), 40L}));

        var table = service.getFastChargePowerTable(false);

        FastChargePower zoe = table.forModel("ZOE");
        assertThat(zoe.source()).isEqualTo(FastChargePower.Source.FLEET_MEDIAN);
        assertThat(zoe.kw()).isEqualByComparingTo("98.5");
        assertThat(table.measuredKw("SONSTIGE_CUSTOM")).isNull();
        assertThat(table.measuredKw("MODEL_3")).isEqualByComparingTo("112.0");
    }
}
