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

    // ---- sanitizeSpeedPair --------------------------------------------------

    @Test
    void sanitizeSpeedPair_passesPlausibleValues() {
        BigDecimal[] out = TessieProcessorService.sanitizeSpeedPair(
                new BigDecimal("72"), new BigDecimal("120"));
        assertEquals(0, out[0].compareTo(new BigDecimal("72.00")));
        assertEquals(0, out[1].compareTo(new BigDecimal("120.00")));
    }

    @Test
    void sanitizeSpeedPair_returnsNullsForNullInputs() {
        BigDecimal[] out = TessieProcessorService.sanitizeSpeedPair(null, null);
        assertNull(out[0]);
        assertNull(out[1]);
    }

    @Test
    void sanitizeSpeedPair_nullsOutOfRangeValues() {
        // > 300 km/h -> implausibel, null statt Insert-Fail durch DB-CHECK
        BigDecimal[] out = TessieProcessorService.sanitizeSpeedPair(
                new BigDecimal("400"), new BigDecimal("500"));
        assertNull(out[0]);
        assertNull(out[1]);
    }

    @Test
    void sanitizeSpeedPair_nullsNegativeValues() {
        BigDecimal[] out = TessieProcessorService.sanitizeSpeedPair(
                new BigDecimal("-5"), new BigDecimal("80"));
        assertNull(out[0]);
        assertEquals(0, out[1].compareTo(new BigDecimal("80.00")));
    }

    @Test
    void sanitizeSpeedPair_nullsBothWhenAvgExceedsMax() {
        // Inkonsistente Quelldaten - DB-CHECK avg<=max wuerde Insert killen.
        BigDecimal[] out = TessieProcessorService.sanitizeSpeedPair(
                new BigDecimal("150"), new BigDecimal("100"));
        assertNull(out[0]);
        assertNull(out[1]);
    }

    @Test
    void sanitizeSpeedPair_allowsAvgEqualsMax() {
        // Sehr kurzer Trip oder Konstantfahrt - avg == max ist legal.
        BigDecimal[] out = TessieProcessorService.sanitizeSpeedPair(
                new BigDecimal("80"), new BigDecimal("80"));
        assertEquals(0, out[0].compareTo(new BigDecimal("80.00")));
        assertEquals(0, out[1].compareTo(new BigDecimal("80.00")));
    }

    @Test
    void sanitizeSpeedPair_independentNullsAreFine() {
        // Nur avg vorhanden (alte Drives ohne max_speed-Feld)
        BigDecimal[] out = TessieProcessorService.sanitizeSpeedPair(
                new BigDecimal("60"), null);
        assertEquals(0, out[0].compareTo(new BigDecimal("60.00")));
        assertNull(out[1]);
    }

    @Test
    void sanitizeSpeedPair_acceptsBoundary300() {
        // 300 km/h ist die Obergrenze laut V118-CHECK - exklusiv -gt 300, also inklusiv 300.
        BigDecimal[] out = TessieProcessorService.sanitizeSpeedPair(
                new BigDecimal("300"), new BigDecimal("300"));
        assertEquals(0, out[0].compareTo(new BigDecimal("300.00")));
        assertEquals(0, out[1].compareTo(new BigDecimal("300.00")));
    }
}
