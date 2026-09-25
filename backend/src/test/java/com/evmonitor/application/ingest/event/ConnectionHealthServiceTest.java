package com.evmonitor.application.ingest.event;

import com.evmonitor.application.ingest.event.ConnectionHealthResponse.ProviderHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Proxy auf {@code /api/internal/connections/summary} (Connectors, R2d) mit echter Jackson-Deserialisierung. */
class ConnectionHealthServiceTest {

    private static final String URL = "http://connectors:8081/api/internal/connections/summary";

    /** Antwort wie Connectors {@code ConnectionSummaryService.Summary}: immer alle vier Provider. */
    private static final String SUMMARY = """
            {"providers":[
              {"provider":"VW_EUDA","total":14,"active":11,"failing":2,"paused":1,"inactive":0,
               "oldestLastSuccessAt":"2026-09-25T08:00:00",
               "topErrors":[{"error":"VwEudaPortalException","count":2}]},
              {"provider":"GOE","total":3,"active":0,"failing":3,"paused":0,"inactive":0,
               "oldestLastSuccessAt":null,"topErrors":[{"error":"Connection refused","count":3}]},
              {"provider":"SMARTCAR","total":0,"active":0,"failing":0,"paused":0,"inactive":0,
               "oldestLastSuccessAt":null,"topErrors":[]},
              {"provider":"TESLA","total":5,"active":5,"failing":0,"paused":0,"inactive":0,
               "oldestLastSuccessAt":null,"topErrors":[]}
            ]}
            """;

    private MockRestServiceServer server;
    private ConnectionHealthService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        service = new ConnectionHealthService(restTemplate, "http://connectors:8081", "secret-token");
    }

    @Test
    void mapsConnectorProvidersToImportCards_withInternalToken() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", "secret-token"))
                .andRespond(withSuccess(SUMMARY, MediaType.APPLICATION_JSON));

        ConnectionHealthResponse health = service.health();

        server.verify();
        assertThat(health.available()).isTrue();
        assertThat(health.providers())
                .extracting(ProviderHealth::provider, ProviderHealth::channel)
                .containsExactly(
                        tuple("VW_GROUP", "SYNC"),
                        tuple("GOE", "SYNC"),
                        tuple("TESLA", "LIVE"));
        ProviderHealth vw = health.providers().get(0);
        assertThat(vw.total()).isEqualTo(14);
        assertThat(vw.active()).isEqualTo(11);
        assertThat(vw.failing()).isEqualTo(2);
        assertThat(vw.paused()).isEqualTo(1);
        assertThat(vw.inactive()).isZero();
        assertThat(vw.oldestLastSuccessAt()).isEqualTo(
                LocalDateTime.parse("2026-09-25T08:00:00").atZone(ZoneId.systemDefault()).toInstant());
        assertThat(vw.topErrors()).containsExactly(new ConnectionHealthResponse.ErrorCount("VwEudaPortalException", 2));
        assertThat(health.providers().get(1).oldestLastSuccessAt()).isNull();
    }

    @Test
    void smartcarMapsToLive_andUnknownProviderIsSkipped() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"providers":[
                  {"provider":"SMARTCAR","total":2,"active":2,"failing":0,"paused":0,"inactive":0,"topErrors":[]},
                  {"provider":"NEU","total":7,"active":7,"failing":0,"paused":0,"inactive":0,"topErrors":[]}
                ]}
                """, MediaType.APPLICATION_JSON));

        List<ProviderHealth> providers = service.health().providers();

        assertThat(providers).extracting(ProviderHealth::provider, ProviderHealth::channel)
                .containsExactly(tuple("SMARTCAR", "LIVE"));
        assertThat(providers.get(0).topErrors()).isEmpty();
    }

    @Test
    void missingEndpoint_isUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertUnavailable(service.health());
    }

    @Test
    void wrongToken_isUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertUnavailable(service.health());
    }

    @Test
    void unreachable_isUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("Connection refused")));

        assertUnavailable(service.health());
    }

    @Test
    void emptyBody_isUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertUnavailable(service.health());
    }

    private static void assertUnavailable(ConnectionHealthResponse health) {
        assertThat(health.available()).isFalse();
        assertThat(health.providers()).isEmpty();
    }
}
