package com.evmonitor.domain.xpeng;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Geteilte Row-Bau-Logik: aus logical-field -> Rohwert + geparstem Timer entsteht
 * ein {@link XpengTelematicsRow}. Wird von XLSX- und CSV-Parser genutzt, damit die
 * Feld-Zuordnung + extras-Aggregation nur an einer Stelle lebt.
 */
class XpengRowMapperTest {

    private static XpengTelematicsRow map(Map<String, String> byLogical, LocalDateTime timer) {
        return XpengRowMapper.map(byLogical::get, timer);
    }

    @Test
    void maptKernfelderUndExtras() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 12, 0, 0);
        Map<String, String> v = new HashMap<>();
        v.put(XpengHeaderMapper.SPEED, "42.5");
        v.put(XpengHeaderMapper.GEAR, "1");
        v.put(XpengHeaderMapper.ODOMETER, "12345.0");
        v.put(XpengHeaderMapper.SOC, "80.0");
        v.put(XpengHeaderMapper.BATT_VOLT, "360.0");
        v.put(XpengHeaderMapper.BATT_CURR, "-15.0");
        v.put(XpengHeaderMapper.CHARGE_POWER, "0.0");
        v.put(XpengHeaderMapper.BATT_TEMP_MAX, "21.0");
        v.put(XpengHeaderMapper.BATT_TEMP_MIN, "20.0");
        v.put(XpengHeaderMapper.BMS_RANGE, "431.0");

        XpengTelematicsRow row = map(v, t);

        assertEquals(t, row.timer());
        assertEquals(0, new BigDecimal("42.5").compareTo(row.vehSpeedKmh()));
        assertEquals(1, row.gearLev());
        assertEquals(0, new BigDecimal("12345.0").compareTo(row.odometerKm()));
        assertEquals(0, new BigDecimal("80.0").compareTo(row.socDisplay()));
        assertEquals(0, new BigDecimal("360.0").compareTo(row.battVolt()));
        assertEquals(0, new BigDecimal("431.0").compareTo(row.extra(XpengExtraKeys.BMS_RANGE_KM)));
    }

    @Test
    void leereUndFehlendeFelderWerdenNull() {
        Map<String, String> v = new HashMap<>();
        v.put(XpengHeaderMapper.SPEED, "");   // leer
        // GEAR fehlt komplett
        XpengTelematicsRow row = map(v, LocalDateTime.of(2026, 9, 1, 12, 0, 0));

        assertNull(row.vehSpeedKmh());
        assertNull(row.gearLev());
        assertNull(row.socDisplay());
        assertTrue(row.extras().isEmpty());
    }

    @Test
    void socSentinelAusserhalb0Bis100WirdNull() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 12, 0, 0);
        Map<String, String> v = new HashMap<>();

        v.put(XpengHeaderMapper.SOC, "255"); // 0xFF-Sentinel beim Aufwachen
        assertNull(map(v, t).socDisplay(), "SoC > 100 muss null werden");

        v.put(XpengHeaderMapper.SOC, "-1");
        assertNull(map(v, t).socDisplay(), "negativer SoC muss null werden");

        v.put(XpengHeaderMapper.SOC, "0"); // leere Batterie ist gueltig
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(map(v, t).socDisplay()));

        v.put(XpengHeaderMapper.SOC, "80");
        assertEquals(0, new BigDecimal("80").compareTo(map(v, t).socDisplay()));
    }

    @Test
    void chargePowerSentinelWirdVomRecordEntschaerft() {
        Map<String, String> v = new HashMap<>();
        v.put(XpengHeaderMapper.CHARGE_POWER, "1638.3"); // > 400 kW Glitch
        XpengTelematicsRow row = map(v, LocalDateTime.of(2026, 9, 1, 12, 0, 0));

        assertNull(row.chargePowerKw(), "unplausible Ladeleistung muss null werden");
    }
}
