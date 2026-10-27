package com.evmonitor.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Proxy zum quellenneutralen Live-Endpunkt der Connectors. */
class LiveChargingServiceTest {

    private static final String BASE = "http://connectors";
    private static final UUID CAR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private MockRestServiceServer server;
    private LiveChargingService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new LiveChargingService(restTemplate, BASE, "token");
    }

    @Test
    void getLiveCharging_callsSourceNeutralPath_andMapsIntervalAndEnergy() {
        server.expect(requestTo(BASE + "/api/internal/live/" + CAR_ID + "/charging"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", "token"))
                .andRespond(withSuccess("""
                        {"isActive":true,"socPercent":45,"socAtSessionStart":20,"energyAddedKwh":12.4,
                         "sessionStartedAt":"2026-10-27T10:00:00Z","updateIntervalSeconds":300}
                        """, MediaType.APPLICATION_JSON));

        LiveChargingResponse r = service.getLiveCharging(CAR_ID);

        assertTrue(r.isActive());
        assertNull(r.powerKw());
        assertEquals(0, new BigDecimal("12.4").compareTo(r.energyAddedKwh()));
        assertEquals(300, r.updateIntervalSeconds());
        server.verify();
    }

    @Test
    void getLiveCharging_noSource404_returnsInactive() {
        server.expect(requestTo(BASE + "/api/internal/live/" + CAR_ID + "/charging"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(service.getLiveCharging(CAR_ID).isActive());
    }

    @Test
    void getLiveChargingHistory_keepsSocPointsWithoutPower() {
        server.expect(requestTo(BASE + "/api/internal/live/" + CAR_ID
                        + "/charging/history?from=2026-10-27T10%3A00%3A00Z&to=2026-10-27T11%3A00%3A00Z"))
                .andRespond(withSuccess("""
                        {"points":[{"timestamp":"2026-10-27T10:05:00Z","powerKw":null,"socPercent":21.0}]}
                        """, MediaType.APPLICATION_JSON));

        LiveChargingHistoryResponse r = service.getLiveChargingHistory(
                CAR_ID, "2026-10-27T10:00:00Z", "2026-10-27T11:00:00Z");

        assertEquals(1, r.points().size());
        assertNull(r.points().get(0).powerKw());
        assertEquals(21.0, r.points().get(0).socPercent());
    }

    @Test
    void getLiveChargingHistory_queryDelimitersInParam_cannotInjectParams() {
        server.expect(requestTo(BASE + "/api/internal/live/" + CAR_ID
                        + "/charging/history?from=2026-10-27T10%3A00%3A00Z&to=x%26carId%3Dother"))
                .andRespond(withSuccess("{\"points\":[]}", MediaType.APPLICATION_JSON));

        service.getLiveChargingHistory(CAR_ID, "2026-10-27T10:00:00Z", "x&carId=other");

        server.verify();
    }
}
