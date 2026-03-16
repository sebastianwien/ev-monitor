package com.evmonitor.application.spritmonitor;

import com.evmonitor.domain.ChargingType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpritMonitorFuelingDTO.parseChargingType() to ensure robust AC/DC detection
 * without false positives from partial string matches (e.g., "ADVANCE" contains "AC").
 */
class SpritMonitorFuelingDTOTest {

    @Test
    void parseChargingType_nullChargeInfo_returnsUnknown() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_emptyChargeInfo_returnsUnknown() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "");
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_blankChargeInfo_returnsUnknown() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "   ");
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_standaloneAC_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "AC");
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_standaloneDC_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DC");
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_acLowerCase_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "ac");
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_dcMixedCase_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "Dc");
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_acWithPower_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "AC 11 kW");
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_dcWithPower_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DC 50kW");
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_commaSeparated_acType2_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "AC, Type 2, 11 kW");
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_commaSeparated_dcCcs_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DC, CCS, 50 kW");
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_commaSeparated_dcTakesPrecedence() {
        // Both DC and AC present - DC takes precedence
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DC 50kW, AC backup");
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_falsePositive_advance_returnsUnknown() {
        // "ADVANCE" contains "AC" but should not match
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "ADVANCE");
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_falsePositive_redcar_returnsUnknown() {
        // "REDCAR" contains "DC" but should not match
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "REDCAR");
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_falsePositive_dacia_returnsUnknown() {
        // "DACIA" contains "AC" but should not match
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DACIA Spring");
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_realWorld_ionityDC_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "IONITY, DC 350kW, CCS");
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_realWorld_wallboxAC_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "Wallbox, AC 11kW, Type 2");
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_noAcDcKeyword_returnsUnknown() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "Type 2, 11 kW");
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    /**
     * Regression test: SpritMonitor API returns "" instead of null for optional BigDecimal fields
     * like odometer, cost, percent, charging_power. Jackson throws an exception by default.
     * The RestTemplateConfig must configure ACCEPT_EMPTY_STRING_AS_NULL_OBJECT.
     */
    @Test
    void deserialize_emptyStringBigDecimalFields_treatedAsNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        String json = """
            {
              "date": "15.01.2024",
              "quantity": 50.0,
              "quantityunitid": 5,
              "odometer": "",
              "cost": "",
              "charging_duration": 60,
              "percent": "",
              "charging_power": ""
            }
            """;

        SpritMonitorFuelingDTO dto = mapper.readValue(json, SpritMonitorFuelingDTO.class);

        assertNotNull(dto);
        assertEquals("15.01.2024", dto.date());
        assertNull(dto.odometer(), "Empty string odometer must deserialize to null");
        assertNull(dto.cost(), "Empty string cost must deserialize to null");
        assertNull(dto.percent(), "Empty string percent must deserialize to null");
        assertNull(dto.chargingPower(), "Empty string charging_power must deserialize to null");
    }
}
