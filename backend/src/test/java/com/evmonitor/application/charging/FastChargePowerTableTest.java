package com.evmonitor.application.charging;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FastChargePowerTableTest {

    private static FastChargePowerTable.Measurement m(String model, String kw, int sessions) {
        return new FastChargePowerTable.Measurement(model, new BigDecimal(kw), sessions);
    }

    @Test
    void aModelWithEnoughSessionsKeepsItsMeasurementWhetherRankedOrNot() {
        var table = FastChargePowerTable.from(List.of(m("MODEL_3", "111.94", 120), m("XPENG_G9", "249.0", 11)));

        FastChargePower g9 = table.forModel("XPENG_G9");
        assertThat(g9.kw()).isEqualByComparingTo("249.0");
        assertThat(g9.source()).isEqualTo(FastChargePower.Source.MEASURED);
        assertThat(g9.sessionCount()).isEqualTo(11);
        // one decimal, like the ranking always showed it
        assertThat(table.forModel("MODEL_3").kw()).isEqualByComparingTo("111.9");
        assertThat(table.measuredKw("MODEL_3")).isEqualByComparingTo("111.9");
    }

    @Test
    void belowEightSessionsTheModelGetsTheMedianOfTheMeasuredModels() {
        // Gemessen: 85, 104, 112, 249 → Median (104 + 112) / 2 = 108; EQA mit 6 Ladungen zählt nicht mit
        var table = FastChargePowerTable.from(List.of(
                m("ID_3", "85", 300), m("ID_4", "104", 80), m("MODEL_3", "112", 120), m("XPENG_G6", "249", 90),
                m("EQA", "76", 6)));

        FastChargePower eqa = table.forModel("EQA");
        assertThat(eqa.kw()).isEqualByComparingTo("108.0");
        assertThat(eqa.source()).isEqualTo(FastChargePower.Source.FLEET_MEDIAN);
        assertThat(eqa.sessionCount()).isEqualTo(6);
        assertThat(table.measuredKw("EQA")).isNull();
        assertThat(table.forModel("ZOE").source()).isEqualTo(FastChargePower.Source.FLEET_MEDIAN);
        assertThat(table.forModel("ZOE").sessionCount()).isZero();
    }

    @Test
    void withoutAnyMeasuredModelTheFixedDefaultApplies() {
        var table = FastChargePowerTable.from(List.of(m("EQA", "76", 6)));

        FastChargePower p = table.forModel("ID_3");
        assertThat(p.kw()).isEqualByComparingTo(FastChargePower.DEFAULT_KW);
        assertThat(p.source()).isEqualTo(FastChargePower.Source.DEFAULT);
        assertThat(FastChargePowerTable.from(List.of()).forModel("ID_3").source()).isEqualTo(FastChargePower.Source.DEFAULT);
    }
}
