package com.evmonitor.application.spritmonitor;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.RouteType;
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
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_emptyChargeInfo_returnsUnknown() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "", null);
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_blankChargeInfo_returnsUnknown() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "   ", null);
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_standaloneAC_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "AC", null);
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_standaloneDC_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DC", null);
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_acLowerCase_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "ac", null);
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_dcMixedCase_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "Dc", null);
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_acWithPower_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "AC 11 kW", null);
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_dcWithPower_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DC 50kW", null);
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_commaSeparated_acType2_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "AC, Type 2, 11 kW", null);
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_commaSeparated_dcCcs_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DC, CCS, 50 kW", null);
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_commaSeparated_dcTakesPrecedence() {
        // Both DC and AC present - DC takes precedence
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DC 50kW, AC backup", null);
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_falsePositive_advance_returnsUnknown() {
        // "ADVANCE" contains "AC" but should not match
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "ADVANCE", null);
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_falsePositive_redcar_returnsUnknown() {
        // "REDCAR" contains "DC" but should not match
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "REDCAR", null);
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_falsePositive_dacia_returnsUnknown() {
        // "DACIA" contains "AC" but should not match
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "DACIA Spring", null);
        assertEquals(ChargingType.UNKNOWN, dto.parseChargingType());
    }

    @Test
    void parseChargingType_realWorld_ionityDC_returnsDC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "IONITY, DC 350kW, CCS", null);
        assertEquals(ChargingType.DC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_realWorld_wallboxAC_returnsAC() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "Wallbox, AC 11kW, Type 2", null);
        assertEquals(ChargingType.AC, dto.parseChargingType());
    }

    @Test
    void parseChargingType_noAcDcKeyword_returnsUnknown() {
        var dto = new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, "Type 2, 11 kW", null);
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

    private static SpritMonitorFuelingDTO withStreets(String streets) {
        return new SpritMonitorFuelingDTO(null, null, null, null, null, null, null, null, null, null, null, null, streets);
    }

    @Test
    void parseRouteType_nullStreets_returnsNull() {
        assertNull(withStreets(null).parseRouteType());
    }

    @Test
    void parseRouteType_blankStreets_returnsNull() {
        assertNull(withStreets("   ").parseRouteType());
    }

    @Test
    void parseRouteType_autobahnOnly_returnsHighway() {
        assertEquals(RouteType.HIGHWAY, withStreets("autobahn").parseRouteType());
    }

    @Test
    void parseRouteType_autobahnUppercase_returnsHighway() {
        assertEquals(RouteType.HIGHWAY, withStreets("AUTOBAHN").parseRouteType());
    }

    @Test
    void parseRouteType_cityOnly_returnsCity() {
        assertEquals(RouteType.CITY, withStreets("city").parseRouteType());
    }

    @Test
    void parseRouteType_landOnly_returnsCombined() {
        assertEquals(RouteType.COMBINED, withStreets("land").parseRouteType());
    }

    @Test
    void parseRouteType_autobahnAndCity_returnsCombined() {
        assertEquals(RouteType.COMBINED, withStreets("autobahn,city").parseRouteType());
    }

    @Test
    void parseRouteType_autobahnAndLand_returnsCombined() {
        assertEquals(RouteType.COMBINED, withStreets("autobahn,land").parseRouteType());
    }

    @Test
    void parseRouteType_allThree_returnsCombined() {
        assertEquals(RouteType.COMBINED, withStreets("autobahn,city,land").parseRouteType());
    }

    @Test
    void parseRouteType_withWhitespace_parsesCorrectly() {
        assertEquals(RouteType.HIGHWAY, withStreets("  autobahn  ").parseRouteType());
        assertEquals(RouteType.COMBINED, withStreets(" autobahn , land ").parseRouteType());
    }

    @Test
    void parseRouteType_unknownTokenOnly_returnsNull() {
        assertNull(withStreets("motorway").parseRouteType());
    }

    @Test
    void parseRouteType_unknownTokenMixedWithAutobahn_returnsHighway() {
        assertEquals(RouteType.HIGHWAY, withStreets("autobahn,motorway").parseRouteType());
    }
}
