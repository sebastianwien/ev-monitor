package com.evmonitor.infrastructure.web;

import com.evmonitor.testutil.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the live springdoc-generated OpenAPI schema reflects the kwh_at_vehicle
 * change: the new field is present, kwh is no longer required, and the @AssertTrue
 * validator method does not leak into the schema as a bogus boolean property.
 */
class OpenApiSchemaTest extends AbstractIntegrationTest {

    @Test
    void sessionEntrySchema_includesKwhAtVehicle_andDoesNotLeakAssertTrue() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertEquals(200, response.getStatusCode().value());

        JsonNode root = new ObjectMapper().readTree(response.getBody());
        JsonNode entry = root.path("components").path("schemas").path("SessionEntry");
        assertFalse(entry.isMissingNode(), "SessionEntry schema missing");

        JsonNode props = entry.path("properties");
        assertTrue(props.has("kwh"), "kwh property missing");
        assertTrue(props.has("kwh_at_vehicle"), "kwh_at_vehicle property missing");
        assertFalse(props.has("energyProvided"), "@AssertTrue method leaked as schema property");
        assertFalse(props.has("raw_import_data"), "raw_import_data is an internal audit field and must not appear in the public API schema");

        JsonNode required = entry.path("required");
        boolean kwhRequired = false;
        for (JsonNode r : required) if ("kwh".equals(r.asText())) kwhRequired = true;
        assertFalse(kwhRequired, "kwh must no longer be required");
    }

    /**
     * The trip schema is a published contract - speeds and the climate summary must show up
     * in snake_case, and the raw {@code telemetry_extras} blob must never leak into it
     * (source-specific and schema-versioned, so clients must not bind against it).
     */
    @Test
    void tripSchema_exposesSpeedsAndClimate_butNotRawTelemetryExtras() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertEquals(200, response.getStatusCode().value());

        JsonNode schemas = new ObjectMapper().readTree(response.getBody())
                .path("components").path("schemas");
        JsonNode tripProps = schemas.path("ApiTripResponse").path("properties");
        assertFalse(tripProps.isMissingNode(), "ApiTripResponse schema missing");

        assertTrue(tripProps.has("avg_speed_kmh"), "avg_speed_kmh missing");
        assertTrue(tripProps.has("max_speed_kmh"), "max_speed_kmh missing");
        assertTrue(tripProps.has("climate"), "climate missing");
        assertFalse(tripProps.has("telemetry_extras"), "raw telemetry_extras must stay internal");
        assertFalse(tripProps.has("telemetryExtras"), "raw telemetry_extras must stay internal");

        JsonNode climateProps = schemas.path("ApiClimateSummary").path("properties");
        assertFalse(climateProps.isMissingNode(), "ApiClimateSummary schema missing");
        for (String field : new String[] {
                "trip_seconds", "comfort_heat", "hvac_heating", "hvac_cooling", "battery_heater"}) {
            assertTrue(climateProps.has(field), field + " missing from climate schema");
        }
    }
}
