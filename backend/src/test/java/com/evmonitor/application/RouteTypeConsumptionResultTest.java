package com.evmonitor.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTypeConsumptionResultTest {

    @Test
    void typedMeanWeighsEachRouteTypeByItsLogs() {
        // ID.3 as measured on 11.10.2026: city 19.1 (113), combined 17.1 (768), highway 18.9 (194)
        var r = new RouteTypeConsumptionResult(new BigDecimal("19.1"), new BigDecimal("17.1"), new BigDecimal("18.9"), 113, 768, 194, 16);

        assertThat(r.typedMeanKwhPer100km()).isEqualByComparingTo("17.6");
    }

    @Test
    void typedMeanSkipsMissingTypesAndIsNullWithoutAny() {
        var highwayOnly = new RouteTypeConsumptionResult(null, null, new BigDecimal("20.5"), 0, 0, 23, 4);
        assertThat(highwayOnly.typedMeanKwhPer100km()).isEqualByComparingTo("20.5");
        assertThat(RouteTypeConsumptionResult.empty().typedMeanKwhPer100km()).isNull();
    }
}
