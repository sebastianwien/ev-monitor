package com.evmonitor.application.charging;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class HomeChargeTest {

    @Test
    void plugKwh_sechzigProzentDesNettoAkkusDurchDieAcVerluste() {
        // 77 × 0,6 / 0,95 = 48,63…
        assertEquals(new BigDecimal("48.63"), HomeCharge.plugKwh(new BigDecimal("77"), new BigDecimal("0.95")));
    }

    @Test
    void plugKwh_ohneAkkuOderWirkungsgrad_null() {
        assertNull(HomeCharge.plugKwh(null, new BigDecimal("0.95")));
        assertNull(HomeCharge.plugKwh(new BigDecimal("77"), null));
        assertNull(HomeCharge.plugKwh(new BigDecimal("77"), BigDecimal.ZERO));
        assertNull(HomeCharge.plugKwh(BigDecimal.ZERO, new BigDecimal("0.95")));
    }
}
