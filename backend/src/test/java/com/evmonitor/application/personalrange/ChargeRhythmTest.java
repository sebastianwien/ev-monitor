package com.evmonitor.application.personalrange;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Same table as frontend utils/chargeRhythm.ts: the result mail says it like the model page. */
class ChargeRhythmTest {

    @Test
    void moreThanOnceAWeekCountsChargesWithOneDecimal() {
        assertThat(ChargeRhythm.text(new BigDecimal("2.3"), true, "de")).isEqualTo("2,3× pro Woche");
        assertThat(ChargeRhythm.text(new BigDecimal("2.0"), true, "de")).isEqualTo("2× pro Woche");
        assertThat(ChargeRhythm.text(new BigDecimal("2.3"), true, "en")).isEqualTo("2.3× a week");
    }

    @Test
    void halfToOnceAWeekCountsDays() {
        // 7 / 0.67 = 10.4 → every 10 days
        assertThat(ChargeRhythm.text(new BigDecimal("0.67"), true, "de")).isEqualTo("alle 10 Tage");
        assertThat(ChargeRhythm.text(BigDecimal.ONE, true, "de")).isEqualTo("alle 7 Tage");
        assertThat(ChargeRhythm.text(new BigDecimal("0.67"), true, "en")).isEqualTo("every 10 days");
    }

    @Test
    void lessThanHalfAWeekCountsWeeks() {
        // 1 / 0.3 = 3.3 → every 3 weeks; at least 2
        assertThat(ChargeRhythm.text(new BigDecimal("0.3"), true, "de")).isEqualTo("alle 3 Wochen");
        assertThat(ChargeRhythm.text(new BigDecimal("0.49"), true, "de")).isEqualTo("alle 2 Wochen");
        assertThat(ChargeRhythm.text(new BigDecimal("0.3"), true, "en")).isEqualTo("every 3 weeks");
    }

    @Test
    void withoutHomeChargingCountsStopsRoundedUp() {
        assertThat(ChargeRhythm.text(new BigDecimal("1.22"), false, "de")).isEqualTo("2 Ladestopps pro Woche");
        assertThat(ChargeRhythm.text(new BigDecimal("0.4"), false, "de")).isEqualTo("1 Ladestopp pro Woche");
        assertThat(ChargeRhythm.text(new BigDecimal("1.22"), false, "en")).isEqualTo("2 charging stops a week");
    }

    @Test
    void noCommuteNeedsNoCharging() {
        assertThat(ChargeRhythm.text(BigDecimal.ZERO, true, "de")).isEqualTo("kein Laden nötig");
        assertThat(ChargeRhythm.text(BigDecimal.ZERO, false, "en")).isEqualTo("no charging needed");
    }
}
