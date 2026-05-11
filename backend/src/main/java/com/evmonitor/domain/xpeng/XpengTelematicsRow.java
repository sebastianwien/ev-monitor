package com.evmonitor.domain.xpeng;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the XPeng TELEMATICS_DATA sheet (sampled every 5s).
 * All numeric fields are nullable - XPeng emits sparse rows when the car is asleep.
 *
 * Gear levels per DATA_CATALOGUE: 1=D, 2=N, 3=R, 4=P.
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
        BigDecimal battTempMinC
) {
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
