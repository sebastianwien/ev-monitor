package com.evmonitor.infrastructure.external.audit;

import com.evmonitor.application.seo.SeoAudit.Vitals;
import com.evmonitor.application.seo.SeoAuditProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Erfundene Domain und Antworten im Format der PageSpeed Insights API v5; HttpClient gemockt. */
class PageSpeedClientTest {

    private final HttpClient http = mock(HttpClient.class);
    private final SeoAuditProperties properties = new SeoAuditProperties();
    private final PageSpeedClient client;

    PageSpeedClientTest() {
        properties.setPagespeedApiKey("key-1");
        client = new PageSpeedClient(properties, new ObjectMapper(), http);
    }

    @SuppressWarnings("unchecked")
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    @SuppressWarnings("unchecked")
    void prefersUrlFieldDataAndSendsMobileStrategy() throws Exception {
        respond(200, """
                {"loadingExperience":{"id":"https://example.test/a","metrics":{
                  "LARGEST_CONTENTFUL_PAINT_MS":{"percentile":3100},
                  "CUMULATIVE_LAYOUT_SHIFT_SCORE":{"percentile":12},
                  "INTERACTION_TO_NEXT_PAINT":{"percentile":180}}},
                 "lighthouseResult":{"audits":{"largest-contentful-paint":{"numericValue":2000.4}}}}
                """);

        assertThat(client.measure("https://example.test/a")).contains(new Vitals(3100, 0.12, 180, "FIELD"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString())
                .contains("url=https%3A%2F%2Fexample.test%2Fa").contains("strategy=mobile").contains("key=key-1");
    }

    @Test
    void fallsBackToLabWithoutInpWhenOnlyOriginData() throws Exception {
        respond(200, """
                {"loadingExperience":{"origin_fallback":true,"metrics":{"LARGEST_CONTENTFUL_PAINT_MS":{"percentile":9999}}},
                 "lighthouseResult":{"audits":{"largest-contentful-paint":{"numericValue":2650.7},
                                               "cumulative-layout-shift":{"numericValue":0.031}}}}
                """);

        assertThat(client.measure("https://example.test/a")).contains(new Vitals(2651, 0.031, null, "LAB"));
    }

    @Test
    void errorOrGarbageIsEmpty() throws Exception {
        respond(429, "{\"error\":{}}");
        assertThat(client.measure("https://example.test/a")).isEmpty();

        respond(200, "kein json");
        assertThat(client.measure("https://example.test/a")).isEmpty();
    }

    @Test
    void disabledWithoutKey() {
        properties.setPagespeedApiKey(" ");
        assertThat(client.isEnabled()).isFalse();
    }
}
