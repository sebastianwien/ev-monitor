package com.evmonitor.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelStoryContextServiceTest {

    private final PublicModelService publicModelService = mock(PublicModelService.class);
    private final ModelStoryContextService service = new ModelStoryContextService(publicModelService);

    private static TopModelResponse top(String model, String fastKw, String summer, String winter) {
        TopModelResponse m = mock(TopModelResponse.class);
        when(m.model()).thenReturn(model);
        when(m.fastChargePowerKw()).thenReturn(fastKw != null ? new BigDecimal(fastKw) : null);
        when(m.summerConsumptionKwhPer100km()).thenReturn(summer != null ? new BigDecimal(summer) : null);
        when(m.winterConsumptionKwhPer100km()).thenReturn(winter != null ? new BigDecimal(winter) : null);
        when(m.minRealConsumptionKwhPer100km()).thenReturn(summer != null ? new BigDecimal(summer).subtract(BigDecimal.ONE) : null);
        when(m.maxRealConsumptionKwhPer100km()).thenReturn(winter != null ? new BigDecimal(winter).add(BigDecimal.ONE) : null);
        return m;
    }

    @Test
    void carriesTheModelsFastChargePowerAndTheFleetWinterMedian() {
        List<TopModelResponse> ranking = List.of(
                top("MODEL_3", "112", "15.0", "18.0"),   // 1.20
                top("ID_3", "95", "16.0", "20.8"),       // 1.30
                top("IONIQ_5", null, "17.0", "23.8"),    // 1.40
                top("EV6", "150", "17.0", null));        // no winter: not in the median
        when(publicModelService.getTopModels(false)).thenReturn(ranking);

        ModelStoryContext c = service.context("Tesla", "Model_3", false).orElseThrow();

        assertThat(c.fastChargePowerKw()).isEqualByComparingTo("112");
        // the model-wide spread of drivers, not the one of a battery variant
        assertThat(c.minRealKwhPer100km()).isEqualByComparingTo("14.0");
        assertThat(c.maxRealKwhPer100km()).isEqualByComparingTo("19.0");
        assertThat(c.fleetWinterFactor()).isEqualByComparingTo("1.30");
        assertThat(c.fleetWinterModels()).isEqualTo(3);
    }

    @Test
    void noFleetFactorBelowThreeModelsAndNoPowerWhenTheModelIsNotListed() {
        List<TopModelResponse> ranking = List.of(top("ID_3", "95", "16.0", "20.8"), top("IONIQ_5", null, "17.0", "23.8"));
        when(publicModelService.getTopModels(false)).thenReturn(ranking);

        ModelStoryContext c = service.context("Tesla", "Model_3", false).orElseThrow();

        assertThat(c.fastChargePowerKw()).isNull();
        assertThat(c.fleetWinterFactor()).isNull();
        assertThat(c.fleetWinterModels()).isEqualTo(2);
    }

    @Test
    void unknownModelIsEmpty() {
        assertThat(service.context("Tesla", "Model_Q", false)).isEmpty();
    }
}
