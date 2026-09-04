package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sammelt waehrend einer Lade-Session sample-level Extras aus
 * {@link XpengTelematicsRow} und liefert am Ende eine JSON-ready Map fuer
 * die {@code ev_log.telemetry_extras}-jsonb-Spalte.
 *
 * Aggregate:
 *  - pack_temp_min/max/avg (aus battTempMaxC/battTempMinC)
 *  - volt_min/max, curr_min/max (Pack-Voltage + -Current)
 *  - bms_range_km.start/end (BMS-Restreichweite zu Session-Beginn/-Ende)
 *  - samples
 */
public class ChargingExtrasAggregator {

    private int samples = 0;
    private BigDecimal packTempMin, packTempMax, packTempSum = BigDecimal.ZERO;
    private int packTempSamples = 0;
    private BigDecimal voltMin, voltMax, currMin, currMax;
    private BigDecimal bmsRangeStart, bmsRangeEnd;

    public void consume(XpengTelematicsRow row) {
        samples++;

        // Pack-Temp ist im Row-Record. Wir gehen davon aus dass max>=min;
        // bilden Pack-Avg als Mittelwert von max+min pro Sample.
        if (row.battTempMaxC() != null) packTempMax = maxOrNull(packTempMax, row.battTempMaxC());
        if (row.battTempMinC() != null) packTempMin = minOrNull(packTempMin, row.battTempMinC());
        if (row.battTempMaxC() != null && row.battTempMinC() != null) {
            BigDecimal mid = row.battTempMaxC().add(row.battTempMinC())
                    .divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
            packTempSum = packTempSum.add(mid);
            packTempSamples++;
        }

        if (row.battVolt() != null) {
            voltMin = minOrNull(voltMin, row.battVolt());
            voltMax = maxOrNull(voltMax, row.battVolt());
        }
        if (row.battCurrent() != null) {
            currMin = minOrNull(currMin, row.battCurrent());
            currMax = maxOrNull(currMax, row.battCurrent());
        }

        BigDecimal range = row.extra(XpengExtraKeys.BMS_RANGE_KM);
        if (range != null) {
            if (bmsRangeStart == null) bmsRangeStart = range;
            bmsRangeEnd = range;
        }
    }

    /** @return JSON-ready Map; null wenn keine Daten gesehen wurden. */
    public Map<String, Object> toMap() {
        if (samples == 0) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "xpeng");
        out.put("schema_version", 1);
        out.put("samples", samples);

        Map<String, Object> battery = new LinkedHashMap<>();
        putIfNotNull(battery, "pack_temp_min_c", scale1(packTempMin));
        putIfNotNull(battery, "pack_temp_max_c", scale1(packTempMax));
        if (packTempSamples > 0) {
            battery.put("pack_temp_avg_c",
                    packTempSum.divide(BigDecimal.valueOf(packTempSamples), 1, RoundingMode.HALF_UP));
        }
        putIfNotNull(battery, "volt_min_v", scale1(voltMin));
        putIfNotNull(battery, "volt_max_v", scale1(voltMax));
        putIfNotNull(battery, "curr_min_a", scale1(currMin));
        putIfNotNull(battery, "curr_max_a", scale1(currMax));
        if (!battery.isEmpty()) out.put("battery", battery);

        if (bmsRangeStart != null || bmsRangeEnd != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            putIfNotNull(range, "start", scale0(bmsRangeStart));
            putIfNotNull(range, "end",   scale0(bmsRangeEnd));
            out.put("bms_range_km", range);
        }
        return out;
    }

    private static BigDecimal maxOrNull(BigDecimal a, BigDecimal b) {
        return a == null ? b : (b.compareTo(a) > 0 ? b : a);
    }

    private static BigDecimal minOrNull(BigDecimal a, BigDecimal b) {
        return a == null ? b : (b.compareTo(a) < 0 ? b : a);
    }

    private static BigDecimal scale1(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale0(BigDecimal v) {
        return v == null ? null : v.setScale(0, RoundingMode.HALF_UP);
    }

    private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }
}
