package com.evmonitor.domain.xpeng;

/**
 * Map-Keys fuer optional erhobene Telematik-Kanaele in {@link XpengTelematicsRow#extras()}.
 * Diese Werte werden nicht in der Hauptverarbeitung (Trip/Session-Detection) benoetigt,
 * sondern fliessen in {@code telemetry_extras}-jsonb pro Session/Trip ein.
 */
public final class XpengExtraKeys {
    private XpengExtraKeys() {}

    public static final String LONG_ACCEL_G       = "long_accel_g";
    public static final String LAT_ACCEL_G        = "lat_accel_g";
    public static final String ACCEL_PEDAL_PCT    = "accel_pedal_pct";
    public static final String FRONT_TORQUE_NM    = "front_torque_nm";
    public static final String REAR_TORQUE_NM     = "rear_torque_nm";
    public static final String FRONT_RPM          = "front_rpm";
    public static final String REAR_RPM           = "rear_rpm";
    public static final String BMS_RANGE_KM       = "bms_range_km";
}
