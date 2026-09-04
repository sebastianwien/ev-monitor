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
        putIfNotNull(extras, XpengExtraKeys.CELL_TEMP_MAX_C, parseDecimal(byLogical.apply(XpengHeaderMapper.CELL_TEMP_MAX)));
        putIfNotNull(extras, XpengExtraKeys.CELL_TEMP_MIN_C, parseDecimal(byLogical.apply(XpengHeaderMapper.CELL_TEMP_MIN)));
        putIfNotNull(extras, XpengExtraKeys.LONG_ACCEL_G,    parseDecimal(byLogical.apply(XpengHeaderMapper.LONG_ACCEL)));
        putIfNotNull(extras, XpengExtraKeys.LAT_ACCEL_G,     parseDecimal(byLogical.apply(XpengHeaderMapper.LAT_ACCEL)));
        putIfNotNull(extras, XpengExtraKeys.ACCEL_PEDAL_PCT, parseDecimal(byLogical.apply(XpengHeaderMapper.ACCEL_PEDAL)));
        putIfNotNull(extras, XpengExtraKeys.FRONT_TORQUE_NM, parseDecimal(byLogical.apply(XpengHeaderMapper.FRONT_TORQUE)));
        putIfNotNull(extras, XpengExtraKeys.REAR_TORQUE_NM,  parseDecimal(byLogical.apply(XpengHeaderMapper.REAR_TORQUE)));
        putIfNotNull(extras, XpengExtraKeys.FRONT_RPM,       parseDecimal(byLogical.apply(XpengHeaderMapper.FRONT_RPM)));
        putIfNotNull(extras, XpengExtraKeys.REAR_RPM,        parseDecimal(byLogical.apply(XpengHeaderMapper.REAR_RPM)));
        putIfNotNull(extras, XpengExtraKeys.BMS_RANGE_KM,    parseDecimal(byLogical.apply(XpengHeaderMapper.BMS_RANGE)));
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
