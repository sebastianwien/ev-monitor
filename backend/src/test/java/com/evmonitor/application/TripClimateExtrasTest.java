package com.evmonitor.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TripClimateExtrasTest {

    @Test
    void parse_validClimateBlob_extractsLoadsAndTripSeconds() {
        String json = """
                {"source":"TESLA_LIVE","schema_version":1,"tripSeconds":1800,
                 "climate":{
                   "comfortHeat":{"active":true,"seconds":1240},
                   "hvacHeating":{"active":true,"seconds":1800},
                   "hvacCooling":{"active":false,"seconds":0},
                   "batteryHeater":{"active":false,"seconds":0}}}""";

        EvTripResponse.ClimateSummary c = TripClimateExtras.parse(json);

        assertThat(c).isNotNull();
        assertThat(c.tripSeconds()).isEqualTo(1800);
        assertThat(c.comfortHeat().active()).isTrue();
        assertThat(c.comfortHeat().seconds()).isEqualTo(1240);
        assertThat(c.hvacHeating().active()).isTrue();
        assertThat(c.hvacCooling().active()).isFalse();
        assertThat(c.batteryHeater().active()).isFalse();
    }

    @Test
    void parse_extrasWithoutClimateNode_returnsNull() {
        // Other telemetry_extras sources (e.g. XPeng driving-style) share the slot with
        // a different schema - they must not yield a climate DTO.
        String json = """
                {"source":"XPENG_IMPORT","schema_version":1,"drivingStyle":{"hardBrakes":3}}""";

        assertThat(TripClimateExtras.parse(json)).isNull();
    }

    @Test
    void parse_allLoadsInactive_returnsNull_soNoMarkerRendered() {
        String json = """
                {"climate":{
                   "comfortHeat":{"active":false,"seconds":0},
                   "hvacHeating":{"active":false,"seconds":0},
                   "hvacCooling":{"active":false,"seconds":0},
                   "batteryHeater":{"active":false,"seconds":0}}}""";

        assertThat(TripClimateExtras.parse(json)).isNull();
    }

    @Test
    void parse_nullOrBlankOrMalformed_returnsNull() {
        assertThat(TripClimateExtras.parse(null)).isNull();
        assertThat(TripClimateExtras.parse("   ")).isNull();
        assertThat(TripClimateExtras.parse("{not json")).isNull();
    }

    @Test
    void parse_missingLoadKeys_defaultToInactiveZero() {
        // comfortHeat present + active, others missing entirely.
        String json = """
                {"tripSeconds":600,"climate":{"comfortHeat":{"active":true,"seconds":600}}}""";

        EvTripResponse.ClimateSummary c = TripClimateExtras.parse(json);

        assertThat(c).isNotNull();
        assertThat(c.comfortHeat().active()).isTrue();
        assertThat(c.hvacHeating().active()).isFalse();
        assertThat(c.hvacHeating().seconds()).isZero();
        assertThat(c.batteryHeater()).isNotNull();
    }
}
