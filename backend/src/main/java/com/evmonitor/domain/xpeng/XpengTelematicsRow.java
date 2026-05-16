package com.evmonitor.domain.xpeng;

import com.evmonitor.domain.EvTrip;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * One row of the XPeng TELEMATICS_DATA sheet (sampled every 5s).
 * All numeric fields are nullable - XPeng emits sparse rows when the car is asleep.
 *
 * Gear levels per DATA_CATALOGUE: 1=D, 2=N, 3=R, 4=P.
 *
 * {@code extras} carries optional sensor channels we don't core-process per row
 * but aggregate into telemetry_extras (driving-style accel, cell temps, motor
 * torque/rpm, BMS range estimate). Keys are defined in {@link XpengExtraKeys}.
 */
public record XpengTelematicsRow(
        LocalDateTime timer,
        BigDecimal vehSpeedKmh,
        Integer gearLev,
        BigDecimal odometerKm,
        BigDecimal socDisplay,
        BigDecimal battVolt,
        BigDecimal battCurrent,
        BigDecimal chargePowerKw,
        BigDecimal battTempMaxC,
        BigDecimal battTempMinC,
        Map<String, BigDecimal> extras
) {
    // XPeng-Sensor liefert vereinzelt Sentinel-Werte (z.B. 1638.3 kW) wenn das
    // Auto aus dem Schlaf wacht. Realer Maximalwert auch fuer DC-Schnelllader
    // liegt unter 400 kW; alles darueber ist ein Glitch und wird zu null
    // entschaerft, damit Detectoren es als "kein Ladevorgang" behandeln.
    private static final BigDecimal MAX_PLAUSIBLE_CHARGE_POWER_KW = new BigDecimal("400");

    public XpengTelematicsRow {
        if (chargePowerKw != null && chargePowerKw.compareTo(MAX_PLAUSIBLE_CHARGE_POWER_KW) > 0) {
            chargePowerKw = null;
        }
        // Speed-Glitches (Sensor-Sentinels) zentral in EvTrip.clampSpeedKmh.
        vehSpeedKmh = EvTrip.clampSpeedKmh(vehSpeedKmh);
        if (extras == null) extras = Map.of();
    }

    /** Convenience: fetch an extras-channel value, null-safe. */
    public BigDecimal extra(String key) {
        return extras.get(key);
    }

    public boolean isDriving() {
        return gearLev != null && (gearLev == 1 || gearLev == 3);
    }

    public boolean isParked() {
        return gearLev != null && gearLev == 4;
    }

    public boolean isMoving() {
        return vehSpeedKmh != null && vehSpeedKmh.signum() > 0;
    }

    public boolean isCharging() {
        return chargePowerKw != null && chargePowerKw.signum() > 0;
    }
}
