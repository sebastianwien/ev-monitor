package com.evmonitor.domain.xpeng;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XpengHeaderMapperTest {

    @Test
    void normalizeLowercasesAndStripsParenSuffix() {
        assertEquals("timer", XpengHeaderMapper.normalize("timer"));
        assertEquals("timer", XpengHeaderMapper.normalize("Timer"));
        assertEquals("timer", XpengHeaderMapper.normalize("timer(GMT+1)"));
        assertEquals("timer", XpengHeaderMapper.normalize("Timer (GMT+2)"));
        assertEquals("esp_vehspd", XpengHeaderMapper.normalize("ESP_VEHSPD"));
        assertNull(XpengHeaderMapper.normalize(null));
    }

    @Test
    void identifiesAllRequiredColumnsInNewSchema() {
        // Headers wie in den Mai-2026 Files
        List<String> headers = List.of(
                "timer", "esp_vehspd", "ldcu_accpedalsig", "eps_steeringangle",
                "ldcu_currentgearlev", "esp_vehlongaccel", "esp_vehlateralaccel",
                "cdcu_totalodometer", "bms_battvolt", "bms_battcurr",
                "ldcu_chrgpwr", "ldcu_bms_soc_disp");
        Map<String, Integer> cols = XpengHeaderMapper.identifyColumns(headers);
        assertTrue(XpengHeaderMapper.isTelematicsSheet(cols));
        assertEquals(0, cols.get(XpengHeaderMapper.TIMER));
        assertEquals(1, cols.get(XpengHeaderMapper.SPEED));
        assertEquals(4, cols.get(XpengHeaderMapper.GEAR));
        assertEquals(7, cols.get(XpengHeaderMapper.ODOMETER));
        assertEquals(10, cols.get(XpengHeaderMapper.CHARGE_POWER));
        assertEquals(11, cols.get(XpengHeaderMapper.SOC));
    }

    @Test
    void identifiesColumnsInOldSchemaWithTimezoneSuffixAndIpbAliases() {
        // Dezember-2025-File-Schema: timer(GMT+1) statt timer, ipb_vehspd statt esp_vehspd
        List<String> headers = List.of(
                "timer(GMT+1)", "ipb_vehspd", "ldcu_accpedalsig",
                "ldcu_currentgearlev", "ipb_vehlongaccel_e2e", "ipb_vehlateralaccel_e2e",
                "cdcu_totalodometer", "ldcu_chrgpwr", "ldcu_bms_soc_disp");
        Map<String, Integer> cols = XpengHeaderMapper.identifyColumns(headers);
        assertTrue(XpengHeaderMapper.isTelematicsSheet(cols),
                "alle 6 Pflicht-Logicals muessen via Aliase aufloesbar sein");
        assertEquals(0, cols.get(XpengHeaderMapper.TIMER));
        assertEquals(1, cols.get(XpengHeaderMapper.SPEED));
        assertEquals(4, cols.get(XpengHeaderMapper.LONG_ACCEL));
        assertEquals(5, cols.get(XpengHeaderMapper.LAT_ACCEL));
    }

    @Test
    void identifiesColumnsInJuli2026SchemaWithoutSensorPrefixes() {
        // Ab 20.07.2026 liefert XPeng die Sensor-Spalten ohne Steuergeraet-Praefix:
        // esp_vehspd -> vehspd, esp_vehlongaccel -> vehlongaccel, ldcu_frwinposstfb -> frwinposstfb
        List<String> headers = List.of(
                "timer", "vehspd", "ldcu_accpedalsig", "eps_steeringangle",
                "ldcu_currentgearlev", "vehlongaccel", "vehlateralaccel",
                "cdcu_totalodometer", "bms_battvolt", "bms_battcurr",
                "ldcu_chrgpwr", "ldcu_bms_soc_disp");
        Map<String, Integer> cols = XpengHeaderMapper.identifyColumns(headers);
        assertTrue(XpengHeaderMapper.isTelematicsSheet(cols),
                "praefixlose Sensor-Spalten muessen aufloesbar sein");
        assertEquals(1, cols.get(XpengHeaderMapper.SPEED));
        assertEquals(5, cols.get(XpengHeaderMapper.LONG_ACCEL));
        assertEquals(6, cols.get(XpengHeaderMapper.LAT_ACCEL));
        assertEquals(7, cols.get(XpengHeaderMapper.ODOMETER));
        assertEquals(10, cols.get(XpengHeaderMapper.CHARGE_POWER));
        assertEquals(11, cols.get(XpengHeaderMapper.SOC));
    }

    @Test
    void resolvesUnknownPrefixVariantOfRequiredColumnViaFallback() {
        // Unbekanntes Praefix auf einer Pflichtspalte: der Fallback strippt Praefixe,
        // damit ein kuenftiger XPeng-Rename nicht wieder den ganzen Import killt.
        List<String> headers = List.of(
                "timer", "abc_vehspd", "ldcu_currentgearlev",
                "cdcu_totalodometer", "ldcu_chrgpwr", "ldcu_bms_soc_disp");
        Map<String, Integer> cols = XpengHeaderMapper.identifyColumns(headers);
        assertTrue(XpengHeaderMapper.isTelematicsSheet(cols),
                "Pflichtspalte mit unbekanntem Praefix muss ueber den Fallback aufloesen");
        assertEquals(1, cols.get(XpengHeaderMapper.SPEED));
    }

    @Test
    void fallbackDoesNotConfuseSocWithOtherBmsColumns() {
        // ldcu_bms_soc_disp endet auf "_disp", bms_battvolt/bms_battcurr duerfen
        // nicht faelschlich als SOC oder umgekehrt aufgeloest werden.
        List<String> headers = List.of(
                "timer", "vehspd", "ldcu_currentgearlev", "cdcu_totalodometer",
                "ldcu_chrgpwr", "bms_battvolt", "bms_battcurr", "ldcu_bms_soc_disp");
        Map<String, Integer> cols = XpengHeaderMapper.identifyColumns(headers);
        assertEquals(7, cols.get(XpengHeaderMapper.SOC));
        assertEquals(5, cols.get(XpengHeaderMapper.BATT_VOLT));
        assertEquals(6, cols.get(XpengHeaderMapper.BATT_CURR));
    }

    @Test
    void rejectsSheetWithoutRequiredColumns() {
        // BASIC_VEHICLE_DATA-style: VIN, Model usw. - kein Telematics-Sheet
        List<String> headers = List.of("VIN", "Model", "Color", "Production Date");
        assertFalse(XpengHeaderMapper.isTelematicsSheet(XpengHeaderMapper.identifyColumns(headers)));
    }

    @Test
    void rejectsSheetMissingChargePower() {
        // Hypothetisch: ohne ldcu_chrgpwr ist es kein verwertbares Telematics-Sheet
        List<String> headers = List.of(
                "timer", "esp_vehspd", "ldcu_currentgearlev",
                "cdcu_totalodometer", "ldcu_bms_soc_disp");
        assertFalse(XpengHeaderMapper.isTelematicsSheet(XpengHeaderMapper.identifyColumns(headers)));
    }
}
