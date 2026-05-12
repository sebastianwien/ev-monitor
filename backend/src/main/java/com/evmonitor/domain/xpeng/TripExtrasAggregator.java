package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sammelt waehrend einer Fahrt sample-level Extras aus
 * {@link XpengTelematicsRow} und liefert am Ende eine JSON-ready Map fuer
 * die {@code ev_trip.telemetry_extras}-jsonb-Spalte.
 *
 * Aggregate:
 *  - driving_style: max long/lat-accel, Anzahl Harsh-Events
 *  - accel_pedal_avg/max
 *  - battery pack_temp_min/max/avg
 *  - motor: max torque + max rpm vorn/hinten, rear_motor_active
 *  - bms_range_km.start/end
 */
public class TripExtrasAggregator {

    // Schwellwerte fuer "harsh events" - branchenueblich
    private static final BigDecimal HARSH_ACCEL_G   = new BigDecimal("0.30");
    private static final BigDecimal HARSH_BRAKE_G   = new BigDecimal("-0.30");
    private static final BigDecimal HARSH_CORNER_G  = new BigDecimal("0.40");
    private static final BigDecimal REAR_MOTOR_ACTIVE_THRESHOLD = new BigDecimal("5");

    private int samples = 0;

    private BigDecimal longAccelMax, longAccelMin;
    private BigDecimal latAccelMax;
    private int harshAccelCount = 0;
    private int harshBrakeCount = 0;
    private int harshCornerCount = 0;

    private BigDecimal pedalSum = BigDecimal.ZERO;
    private int pedalSamples = 0;
    private BigDecimal pedalMax;

    private BigDecimal packTempMin, packTempMax, packTempSum = BigDecimal.ZERO;
    private int packTempSamples = 0;

    private BigDecimal frontTorqueMax, rearTorqueMax, frontRpmMax, rearRpmMax;
    private boolean rearMotorActive = false;

    private BigDecimal bmsRangeStart, bmsRangeEnd;

    public void consume(XpengTelematicsRow row) {
        samples++;

        BigDecimal longAccel = row.extra(XpengExtraKeys.LONG_ACCEL_G);
        if (longAccel != null) {
            longAccelMax = maxOrNull(longAccelMax, longAccel);
            longAccelMin = minOrNull(longAccelMin, longAccel);
            if (longAccel.compareTo(HARSH_ACCEL_G) > 0) harshAccelCount++;
            if (longAccel.compareTo(HARSH_BRAKE_G) < 0) harshBrakeCount++;
        }
        BigDecimal latAccel = row.extra(XpengExtraKeys.LAT_ACCEL_G);
        if (latAccel != null) {
            BigDecimal absLat = latAccel.abs();
            latAccelMax = maxOrNull(latAccelMax, absLat);
            if (absLat.compareTo(HARSH_CORNER_G) > 0) harshCornerCount++;
        }

        BigDecimal pedal = row.extra(XpengExtraKeys.ACCEL_PEDAL_PCT);
        if (pedal != null) {
            pedalSum = pedalSum.add(pedal);
            pedalSamples++;
            pedalMax = maxOrNull(pedalMax, pedal);
        }

        if (row.battTempMaxC() != null) packTempMax = maxOrNull(packTempMax, row.battTempMaxC());
        if (row.battTempMinC() != null) packTempMin = minOrNull(packTempMin, row.battTempMinC());
        if (row.battTempMaxC() != null && row.battTempMinC() != null) {
            BigDecimal mid = row.battTempMaxC().add(row.battTempMinC())
                    .divide(BigDecimal.valueOf(2), MathContext.DECIMAL64);
            packTempSum = packTempSum.add(mid);
            packTempSamples++;
        }

        BigDecimal frontTorque = row.extra(XpengExtraKeys.FRONT_TORQUE_NM);
        BigDecimal rearTorque  = row.extra(XpengExtraKeys.REAR_TORQUE_NM);
        BigDecimal frontRpm    = row.extra(XpengExtraKeys.FRONT_RPM);
        BigDecimal rearRpm     = row.extra(XpengExtraKeys.REAR_RPM);
        if (frontTorque != null) frontTorqueMax = maxOrNull(frontTorqueMax, frontTorque.abs());
        if (rearTorque != null) {
            rearTorqueMax = maxOrNull(rearTorqueMax, rearTorque.abs());
            if (rearTorque.abs().compareTo(REAR_MOTOR_ACTIVE_THRESHOLD) > 0) rearMotorActive = true;
        }
        if (frontRpm != null) frontRpmMax = maxOrNull(frontRpmMax, frontRpm.abs());
        if (rearRpm != null) rearRpmMax = maxOrNull(rearRpmMax, rearRpm.abs());

        BigDecimal range = row.extra(XpengExtraKeys.BMS_RANGE_KM);
        if (range != null) {
            if (bmsRangeStart == null) bmsRangeStart = range;
            bmsRangeEnd = range;
        }
    }

    public Map<String, Object> toMap() {
        if (samples == 0) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "xpeng");
        out.put("schema_version", 1);
        out.put("samples", samples);

        Map<String, Object> driving = new LinkedHashMap<>();
        putIfNotNull(driving, "max_long_accel_g", scale2(longAccelMax));
        putIfNotNull(driving, "max_long_deccel_g", scale2(longAccelMin));
        putIfNotNull(driving, "max_lat_accel_g", scale2(latAccelMax));
        if (harshAccelCount > 0)  driving.put("harsh_accel_count", harshAccelCount);
        if (harshBrakeCount > 0)  driving.put("harsh_brake_count", harshBrakeCount);
        if (harshCornerCount > 0) driving.put("harsh_corner_count", harshCornerCount);
        if (pedalSamples > 0) {
            driving.put("accel_pedal_avg_pct",
                    pedalSum.divide(BigDecimal.valueOf(pedalSamples), 1, RoundingMode.HALF_UP));
        }
        putIfNotNull(driving, "accel_pedal_max_pct", scale1(pedalMax));
        if (!driving.isEmpty()) out.put("driving_style", driving);

        Map<String, Object> battery = new LinkedHashMap<>();
        putIfNotNull(battery, "pack_temp_min_c", scale1(packTempMin));
        putIfNotNull(battery, "pack_temp_max_c", scale1(packTempMax));
        if (packTempSamples > 0) {
            battery.put("pack_temp_avg_c",
                    packTempSum.divide(BigDecimal.valueOf(packTempSamples), 1, RoundingMode.HALF_UP));
        }
        if (!battery.isEmpty()) out.put("battery", battery);

        Map<String, Object> motor = new LinkedHashMap<>();
        putIfNotNull(motor, "front_torque_max_nm", scale0(frontTorqueMax));
        putIfNotNull(motor, "rear_torque_max_nm", scale0(rearTorqueMax));
        putIfNotNull(motor, "front_rpm_max", scale0(frontRpmMax));
        putIfNotNull(motor, "rear_rpm_max", scale0(rearRpmMax));
        if (rearTorqueMax != null) motor.put("rear_motor_active", rearMotorActive);
        if (!motor.isEmpty()) out.put("motor", motor);

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

    private static BigDecimal scale2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale0(BigDecimal v) {
        return v == null ? null : v.setScale(0, RoundingMode.HALF_UP);
    }

    private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }
}
