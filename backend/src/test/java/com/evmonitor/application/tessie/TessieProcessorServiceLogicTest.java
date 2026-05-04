package com.evmonitor.application.tessie;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure-Java helpers in {@link TessieProcessorService}.
 * Lives next to the IT but never touches Spring or the database, so it always
 * runs even when Docker is not available.
 */
class TessieProcessorServiceLogicTest {

    @Test
    void effectivePower_usesTessieFieldWhenItExceedsDerived() {
        BigDecimal kwh = new BigDecimal("10.0");
        long start = 1_000_000L;
        long end = start + 3600; // 1 hour -> derived = 10 kW
        BigDecimal tessie = new BigDecimal("50.0");

        BigDecimal effective = TessieProcessorService.effectivePowerKw(kwh, start, end, tessie);
        assertEquals(0, effective.compareTo(tessie));
    }

    @Test
    void effectivePower_fallsBackToDerivedWhenTessieFieldMissing() {
        BigDecimal kwh = new BigDecimal("22.0");
        long start = 1_000_000L;
        long end = start + 3600; // 1 hour -> derived = 22 kW

        BigDecimal effective = TessieProcessorService.effectivePowerKw(kwh, start, end, null);
        assertNotNull(effective);
        assertEquals(0, effective.compareTo(new BigDecimal("22.00")));
    }

    @Test
    void effectivePower_returnsNullWhenBothMissing() {
        assertNull(TessieProcessorService.effectivePowerKw(null, 0, 0, null));
    }

    @Test
    void effectivePower_returnsTessieWhenDerivedNotComputable() {
        BigDecimal tessie = new BigDecimal("11.0");
        // duration = 0 -> derived undefined, must fall back to tessie
        BigDecimal effective = TessieProcessorService.effectivePowerKw(new BigDecimal("0.5"), 1000, 1000, tessie);
        assertEquals(0, effective.compareTo(tessie));
    }

    @Test
    void classifyRouteType_returnsHighwayForFastDrives() {
        assertEquals("HIGHWAY", TessieProcessorService.classifyRouteType(new BigDecimal("90")));
        assertEquals("HIGHWAY", TessieProcessorService.classifyRouteType(new BigDecimal("120")));
    }

    @Test
    void classifyRouteType_returnsCityForSlowDrives() {
        assertEquals("CITY", TessieProcessorService.classifyRouteType(new BigDecimal("30")));
        assertEquals("CITY", TessieProcessorService.classifyRouteType(new BigDecimal("59.9")));
    }

    @Test
    void classifyRouteType_returnsCombinedForMidRange() {
        assertEquals("COMBINED", TessieProcessorService.classifyRouteType(new BigDecimal("60")));
        assertEquals("COMBINED", TessieProcessorService.classifyRouteType(new BigDecimal("80")));
    }

    @Test
    void classifyRouteType_returnsNullForMissingSpeed() {
        assertNull(TessieProcessorService.classifyRouteType(null));
    }

    @Test
    void publicThresholdConstantIs11() {
        // Locking the threshold prevents accidental drift; the UI hint and the
        // backend classification must stay in sync at 11 kW.
        assertEquals(0, TessieProcessorService.AC_PUBLIC_THRESHOLD_KW.compareTo(new BigDecimal("11.0")));
    }
}
