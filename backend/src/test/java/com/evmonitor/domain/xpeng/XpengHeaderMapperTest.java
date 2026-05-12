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
