package com.evmonitor.infrastructure.external;

import com.evmonitor.application.spritmonitor.RawFueling;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit test for SpritMonitorClient using MockRestServiceServer.
 *
 * Reproduces the regression where getFuelings() fails with
 * "Error while extracting response for type [class java.lang.String]
 * and content type [application/json;charset=UTF-8]" when the custom
 * Jackson converter sits at position 0 in the RestTemplate.
 */
class SpritMonitorClientTest {

    private SpritMonitorClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = buildSpritMonitorRestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new SpritMonitorClient(restTemplate, "test-app-id");
    }

    @Test
    void getFuelings_withApplicationJsonContentType_returnsAllFuelings() {
        String responseBody = """
                [
                  {
                    "date": "15.01.2024",
                    "quantity": 50.0,
                    "quantityunitid": 5,
                    "cost": 12.50,
                    "charging_duration": 60
                  },
                  {
                    "date": "20.01.2024",
                    "quantity": 30.0,
                    "quantityunitid": 5,
                    "cost": 9.00,
                    "charging_duration": 45
                  }
                ]
                """;

        mockServer.expect(requestTo("https://api.spritmonitor.de/v1/vehicle/123/tank/1/fuelings.json?offset=0&limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        List<RawFueling> fuelings = client.getFuelings("test-token", 123, 1);

        mockServer.verify();
        assertEquals(2, fuelings.size());
        assertEquals("15.01.2024", fuelings.get(0).dto().date());
        assertEquals("20.01.2024", fuelings.get(1).dto().date());
    }

    @Test
    void getFuelings_preservesRawJsonPerEntry() {
        String responseBody = """
                [{"date":"15.01.2024","quantity":50.0,"quantityunitid":5,"future_field":"keep_me"}]
                """;

        mockServer.expect(requestTo("https://api.spritmonitor.de/v1/vehicle/123/tank/1/fuelings.json?offset=0&limit=100"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        List<RawFueling> fuelings = client.getFuelings("test-token", 123, 1);

        assertEquals(1, fuelings.size());
        assertTrue(fuelings.get(0).rawJson().contains("future_field"),
                "Unknown fields must be preserved verbatim in raw JSON");
    }

    @Test
    void getFuelings_emptyArray_returnsEmptyList() {
        mockServer.expect(requestTo("https://api.spritmonitor.de/v1/vehicle/123/tank/1/fuelings.json?offset=0&limit=100"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<RawFueling> fuelings = client.getFuelings("test-token", 123, 1);

        mockServer.verify();
        assertTrue(fuelings.isEmpty());
    }

    private RestTemplate buildSpritMonitorRestTemplate() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        restTemplate.getMessageConverters().add(0, new MappingJackson2HttpMessageConverter(mapper));
        return restTemplate;
    }
}
