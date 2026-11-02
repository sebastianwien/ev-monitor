package com.evmonitor.application.charging;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FastChargeStopTest {

    private static final BigDecimal DC = new BigDecimal("0.95");

    @Test
    void plugKwh_siebzigProzentDesNettoAkkusDurchDieDcVerluste() {
        // 77 × 0,7 / 0,95 = 56,736…
        assertEquals(new BigDecimal("56.74"), FastChargeStop.plugKwh(new BigDecimal("77"), DC));
    }

    @Test
    void plugKwh_ohneAkkuOderWirkungsgrad_null() {
        assertNull(FastChargeStop.plugKwh(null, DC));
        assertNull(FastChargeStop.plugKwh(new BigDecimal("77"), null));
        assertNull(FastChargeStop.plugKwh(new BigDecimal("77"), BigDecimal.ZERO));
        assertNull(FastChargeStop.plugKwh(BigDecimal.ZERO, DC));
    }

    @Test
    void cost_istPlugKwhMalPreis() {
        // 56,736… × 0,59 = 33,47
        assertEquals(new BigDecimal("33.47"), FastChargeStop.cost(new BigDecimal("77"), DC, new BigDecimal("0.59")));
    }

    @Test
    void minutes_siebzigProzentDurchLeistung_aufFuenfGerundet() {
        // 77 × 0,7 / 120 × 60 = 26,95 → 25
        assertEquals(25, FastChargeStop.minutes(new BigDecimal("77"), new BigDecimal("120")));
        // 58 × 0,7 / 85 × 60 = 28,66 → 30
        assertEquals(30, FastChargeStop.minutes(new BigDecimal("58"), new BigDecimal("85")));
    }

    @Test
    void minutes_ohneAkkuOderLeistung_null() {
        assertNull(FastChargeStop.minutes(null, new BigDecimal("120")));
        assertNull(FastChargeStop.minutes(new BigDecimal("77"), null));
        assertNull(FastChargeStop.minutes(new BigDecimal("77"), BigDecimal.ZERO));
        assertNull(FastChargeStop.minutes(BigDecimal.ZERO, new BigDecimal("120")));
    }
}
