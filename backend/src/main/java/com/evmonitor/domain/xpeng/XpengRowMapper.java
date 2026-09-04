package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Baut aus logical-field -> Rohwert (String) einen {@link XpengTelematicsRow}.
 *
 * Geteilt zwischen dem XLSX-Streaming-Parser und dem CSV-Export-Parser: beide
 * loesen ihre Spalten ueber {@link XpengHeaderMapper} auf und unterscheiden sich
 * nur darin, wie sie an die Rohwerte und den Zeitstempel kommen (SAX-Zellen vs.
 * CSV-Felder, Datums-String vs. Epoch-Sekunden). Der Timer wird deshalb bereits
 * geparst hereingereicht.
 */
public final class XpengRowMapper {

    private XpengRowMapper() {}

    /**
     * @param byLogical liefert zu einem logischen Feld aus {@link XpengHeaderMapper}
     *                  den Rohwert, oder {@code null} wenn die Spalte fehlt/leer ist.
     * @param timer     bereits geparster Zeitstempel (non-null).
     */
    public static XpengTelematicsRow map(Function<String, String> byLogical, LocalDateTime timer) {
        Map<String, BigDecimal> extras = new HashMap<>();
        putIfNotNull(extras, XpengExtraKeys.CELL_TEMP_MAX_C, scrub(XpengExtraKeys.CELL_TEMP_MAX_C, parseDecimal(byLogical.apply(XpengHeaderMapper.CELL_TEMP_MAX))));
        putIfNotNull(extras, XpengExtraKeys.CELL_TEMP_MIN_C, scrub(XpengExtraKeys.CELL_TEMP_MIN_C, parseDecimal(byLogical.apply(XpengHeaderMapper.CELL_TEMP_MIN))));
        putIfNotNull(extras, XpengExtraKeys.LONG_ACCEL_G,    scrub(XpengExtraKeys.LONG_ACCEL_G,    parseDecimal(byLogical.apply(XpengHeaderMapper.LONG_ACCEL))));
        putIfNotNull(extras, XpengExtraKeys.LAT_ACCEL_G,     scrub(XpengExtraKeys.LAT_ACCEL_G,     parseDecimal(byLogical.apply(XpengHeaderMapper.LAT_ACCEL))));
        putIfNotNull(extras, XpengExtraKeys.ACCEL_PEDAL_PCT, scrub(XpengExtraKeys.ACCEL_PEDAL_PCT, parseDecimal(byLogical.apply(XpengHeaderMapper.ACCEL_PEDAL))));
        putIfNotNull(extras, XpengExtraKeys.FRONT_TORQUE_NM, scrub(XpengExtraKeys.FRONT_TORQUE_NM, parseDecimal(byLogical.apply(XpengHeaderMapper.FRONT_TORQUE))));
        putIfNotNull(extras, XpengExtraKeys.REAR_TORQUE_NM,  scrub(XpengExtraKeys.REAR_TORQUE_NM,  parseDecimal(byLogical.apply(XpengHeaderMapper.REAR_TORQUE))));
        putIfNotNull(extras, XpengExtraKeys.FRONT_RPM,       scrub(XpengExtraKeys.FRONT_RPM,       parseDecimal(byLogical.apply(XpengHeaderMapper.FRONT_RPM))));
        putIfNotNull(extras, XpengExtraKeys.REAR_RPM,        scrub(XpengExtraKeys.REAR_RPM,        parseDecimal(byLogical.apply(XpengHeaderMapper.REAR_RPM))));
        putIfNotNull(extras, XpengExtraKeys.BMS_RANGE_KM,    scrub(XpengExtraKeys.BMS_RANGE_KM,    parseDecimal(byLogical.apply(XpengHeaderMapper.BMS_RANGE))));
        return new XpengTelematicsRow(
                timer,
                parseDecimal(byLogical.apply(XpengHeaderMapper.SPEED)),
                parseInt(byLogical.apply(XpengHeaderMapper.GEAR)),
                parseDecimal(byLogical.apply(XpengHeaderMapper.ODOMETER)),
                parseDecimal(byLogical.apply(XpengHeaderMapper.SOC)),
                parseDecimal(byLogical.apply(XpengHeaderMapper.BATT_VOLT)),
                parseDecimal(byLogical.apply(XpengHeaderMapper.BATT_CURR)),
                parseDecimal(byLogical.apply(XpengHeaderMapper.CHARGE_POWER)),
                parseDecimal(byLogical.apply(XpengHeaderMapper.BATT_TEMP_MAX)),
                parseDecimal(byLogical.apply(XpengHeaderMapper.BATT_TEMP_MIN)),
                extras);
    }

    private static void putIfNotNull(Map<String, BigDecimal> map, String key, BigDecimal v) {
        if (v != null) map.put(key, v);
    }

    /**
     * "Kein Wert"-Sentinels der optionalen Telematik-Kanaele (physische Werte, wie
     * sie im CSV-Export erscheinen). Diese Marker sind keine Messungen und wuerden
     * jede Auswertung von {@code telemetry_extras} verfaelschen (z.B. Reichweite
     * 1638,3 km, Drehzahl 49535 rpm). Kernfelder (SoC, Ladeleistung, Speed) werden
     * separat in {@link XpengTelematicsRow} bzw. EvTrip.clampSpeedKmh entschaerft.
     */
    private static final Map<String, BigDecimal> EXTRA_SENTINELS = Map.of(
            XpengExtraKeys.BMS_RANGE_KM,   new BigDecimal("1638.3"),
            XpengExtraKeys.FRONT_RPM,      new BigDecimal("49535"),
            XpengExtraKeys.REAR_RPM,       new BigDecimal("49535"),
            // bms_celltempmax/minnum sind Sensor-Indizes (0..63), 63 = kein Messwert.
            XpengExtraKeys.CELL_TEMP_MAX_C, new BigDecimal("63"),
            XpengExtraKeys.CELL_TEMP_MIN_C, new BigDecimal("63"));

    /** Liefert {@code null} wenn {@code v} der Sentinel des Kanals ist, sonst {@code v}. */
    private static BigDecimal scrub(String key, BigDecimal v) {
        if (v == null) return null;
        BigDecimal sentinel = EXTRA_SENTINELS.get(key);
        return (sentinel != null && sentinel.compareTo(v) == 0) ? null : v;
    }

    public static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return (int) Double.parseDouble(raw.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
