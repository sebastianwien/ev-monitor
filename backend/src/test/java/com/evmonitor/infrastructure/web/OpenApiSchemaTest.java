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
}
