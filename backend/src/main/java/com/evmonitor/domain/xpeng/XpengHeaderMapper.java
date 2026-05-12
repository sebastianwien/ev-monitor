package com.evmonitor.domain.xpeng;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bildet XPeng-Excel-Spaltennamen auf logische Felder ab. Robust gegen
 * Schema-Drift (Sheet-Renames, Header-Variationen, alternative Sensoren).
 *
 * Verwendung:
 *  - {@link #identifyColumns(List)} liefert eine logical-name → columnIndex Map
 *  - {@link #isTelematicsSheet(Map)} prueft ob ein Sheet alle Pflicht-Spalten enthaelt
 *
 * Zwei Schichten:
 *  1. Lexikalisch: {@link #normalize(String)} strippt Klammer-Suffixe und lowercased.
 *     "timer(GMT+1)" -> "timer", "ESP_VehSpd" -> "esp_vehspd".
 *  2. Semantisch: {@link #ALIASES} listet pro logischem Feld die akzeptierten
 *     physischen Spalten - z.B. speed = esp_vehspd ODER ipb_vehspd
 *     (XPeng hat das Anfang 2026 umbenannt).
 */
public final class XpengHeaderMapper {

    public static final String TIMER          = "timer";
    public static final String SPEED          = "speed";
    public static final String GEAR           = "gear";
    public static final String ODOMETER       = "odometer";
    public static final String SOC            = "soc";
    public static final String CHARGE_POWER   = "charge_power";
    public static final String BATT_VOLT      = "batt_volt";
    public static final String BATT_CURR      = "batt_curr";
    public static final String BATT_TEMP_MAX  = "batt_temp_max";
    public static final String BATT_TEMP_MIN  = "batt_temp_min";
    public static final String CELL_TEMP_MAX  = "cell_temp_max";
    public static final String CELL_TEMP_MIN  = "cell_temp_min";
    public static final String LONG_ACCEL     = "long_accel";
    public static final String LAT_ACCEL      = "lat_accel";
    public static final String ACCEL_PEDAL    = "accel_pedal";
    public static final String FRONT_TORQUE   = "front_torque";
    public static final String REAR_TORQUE    = "rear_torque";
    public static final String FRONT_RPM      = "front_rpm";
    public static final String REAR_RPM       = "rear_rpm";
    public static final String BMS_RANGE      = "bms_range";

    /** Logical -> akzeptierte physische Spaltennamen (NORMALISIERT). */
    private static final Map<String, Set<String>> ALIASES = Map.ofEntries(
            Map.entry(TIMER,         Set.of("timer")),
            Map.entry(SPEED,         Set.of("esp_vehspd", "ipb_vehspd")),
            Map.entry(GEAR,          Set.of("ldcu_currentgearlev")),
            Map.entry(ODOMETER,      Set.of("cdcu_totalodometer")),
            Map.entry(SOC,           Set.of("ldcu_bms_soc_disp")),
            Map.entry(CHARGE_POWER,  Set.of("ldcu_chrgpwr")),
            Map.entry(BATT_VOLT,     Set.of("bms_battvolt")),
            Map.entry(BATT_CURR,     Set.of("bms_battcurr")),
            Map.entry(BATT_TEMP_MAX, Set.of("bms_batttempmax_gb")),
            Map.entry(BATT_TEMP_MIN, Set.of("bms_batttempmin_gb")),
            Map.entry(CELL_TEMP_MAX, Set.of("bms_celltempmaxnum_gb")),
            Map.entry(CELL_TEMP_MIN, Set.of("bms_celltempminnum_gb")),
            Map.entry(LONG_ACCEL,    Set.of("esp_vehlongaccel", "ipb_vehlongaccel_e2e")),
            Map.entry(LAT_ACCEL,     Set.of("esp_vehlateralaccel", "ipb_vehlateralaccel_e2e")),
            Map.entry(ACCEL_PEDAL,   Set.of("ldcu_accpedalsig")),
            Map.entry(FRONT_TORQUE,  Set.of("ipuf_acttorq")),
            Map.entry(REAR_TORQUE,   Set.of("ipur_acttorq")),
            Map.entry(FRONT_RPM,     Set.of("ipuf_actrotspd")),
            Map.entry(REAR_RPM,      Set.of("ipur_actrotspd")),
            Map.entry(BMS_RANGE,     Set.of("ldcu_dstbatdisp_dynamic"))
    );

    /**
     * Pflichtspalten - wenn ein Sheet diese sechs Felder enthaelt, behandeln wir
     * es als TELEMATICS-Sheet, unabhaengig vom Sheet-Namen. Alles andere ist
     * optional und fliesst in telemetry_extras ein wenn vorhanden.
     */
    public static final List<String> REQUIRED_LOGICAL = List.of(
            TIMER, SPEED, GEAR, ODOMETER, SOC, CHARGE_POWER);

    private XpengHeaderMapper() {}

    /**
     * Normalisiert einen Spaltenkopf:
     *  - trim + lowercase
     *  - entfernt alles ab '(' inkl. (Timezone-Suffixe wie "(GMT+1)")
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        int paren = s.indexOf('(');
        if (paren >= 0) s = s.substring(0, paren).trim();
        return s;
    }

    /**
     * Bildet eine Liste von Spaltennamen (in Reihenfolge des Sheets) auf logische
     * Feldnamen ab. Unbekannte Spalten werden ausgelassen.
     *
     * @return logical-name -> column-index (0-basiert), in stabile Reihenfolge der
     *         logischen Namen aus {@link #ALIASES} eingefuegt.
     */
    public static Map<String, Integer> identifyColumns(List<String> rawHeaders) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (rawHeaders == null) return out;
        for (Map.Entry<String, Set<String>> entry : ALIASES.entrySet()) {
            String logical = entry.getKey();
            Set<String> aliases = entry.getValue();
            for (int i = 0; i < rawHeaders.size(); i++) {
                String norm = normalize(rawHeaders.get(i));
                if (norm != null && aliases.contains(norm)) {
                    out.put(logical, i);
                    break;
                }
            }
        }
        return out;
    }

    /** True wenn alle {@link #REQUIRED_LOGICAL}-Felder aufgeloest wurden. */
    public static boolean isTelematicsSheet(Map<String, Integer> logicalColumns) {
        if (logicalColumns == null) return false;
        for (String req : REQUIRED_LOGICAL) {
            if (!logicalColumns.containsKey(req)) return false;
        }
        return true;
    }
}
