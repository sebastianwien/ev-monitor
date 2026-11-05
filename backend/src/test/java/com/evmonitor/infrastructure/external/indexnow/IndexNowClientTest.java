package com.evmonitor.infrastructure.external.indexnow;

import com.evmonitor.application.seo.SeoAuditProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** IndexNow ohne echten Aufruf: HttpClient gemockt, erfundener Host und Schlüssel. */
class IndexNowClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final SeoAuditProperties properties = new SeoAuditProperties();
    private IndexNowClient client;

    @BeforeEach
    void setUp() {
        properties.setSiteHost("example.test");
        properties.setIndexnowKey("0123456789abcdef0123456789abcdef");
        client = new IndexNowClient(properties, objectMapper, http);
    }

    @Test
    void disabledWithoutKey() {
        properties.setIndexnowKey("");
        assertThat(client.isEnabled()).isFalse();
        assertThat(client.submit(List.of("/a"))).isFalse();
        properties.setIndexnowKey("kurz");
        assertThat(client.isEnabled()).as("unter 8 Zeichen lehnt IndexNow ab").isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void postsAbsoluteUrlsWithKeyLocation() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response(202));

        assertThat(client.submit(List.of("/modelle", "/modelle/Tesla/Model_Y"))).isTrue();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://api.indexnow.org/indexnow");
        JsonNode body = objectMapper.readTree(body(request));
        assertThat(body.path("host").asText()).isEqualTo("example.test");
        assertThat(body.path("key").asText()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(body.path("keyLocation").asText()).isEqualTo("https://example.test/indexnow-key.txt");
        assertThat(body.path("urlList").get(0).asText()).isEqualTo("https://example.test/modelle");
        assertThat(body.path("urlList").get(1).asText()).isEqualTo("https://example.test/modelle/Tesla/Model_Y");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsFailureOnHttpErrorAndSkipsEmptyList() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response(403));

        assertThat(client.submit(List.of("/a"))).isFalse();

        HttpClient untouched = mock(HttpClient.class);
        assertThat(new IndexNowClient(properties, objectMapper, untouched).submit(List.of())).isTrue();
        verify(untouched, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static HttpResponse<String> response(int status) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return null; }
            @Override public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
            @Override public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override public String body() { return ""; }
            @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
            @Override public java.net.URI uri() { return null; }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    private static String body(HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) { sb.append(StandardCharsets.UTF_8.decode(item)); }
            @Override public void onError(Throwable t) { throw new RuntimeException(t); }
            @Override public void onComplete() { }
        });
        return sb.toString();
    }
}
