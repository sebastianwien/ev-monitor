package com.evmonitor.infrastructure.external.gsc;

import com.evmonitor.application.seo.GscProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Token-Tausch und Pagination ohne echte Google-Calls: HttpClient ist gemockt, der RSA-Key wird im Test erzeugt. */
class GscClientTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T04:30:00Z"), ZoneOffset.UTC);
    private static final String TOKEN_URI = "https://oauth2.example.test/token";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private KeyPair keyPair;
    private GscClient client;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String json = objectMapper.writeValueAsString(Map.of(
                "type", "service_account",
                "client_email", "reader@example.iam.test",
                "private_key", pem,
                "token_uri", TOKEN_URI));
        GscProperties props = new GscProperties();
        props.setServiceAccountJson(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        props.setSiteUrl("sc-domain:example.test");
        client = new GscClient(props, objectMapper, http, CLOCK, 2);
    }

    @Test
    void disabledWithoutKey() {
        GscProperties empty = new GscProperties();
        assertThat(new GscClient(empty, objectMapper).isEnabled()).isFalse();
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void exchangesSignedJwtForTokenThenPaginatesUntilShortPage() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                response(200, "{\"access_token\":\"tok-1\",\"expires_in\":3600}"),
                response(200, rows("[\"2026-10-14\",\"https://example.test/a\"]", "[\"2026-10-14\",\"https://example.test/b\"]")),
                response(200, rows("[\"2026-10-14\",\"https://example.test/c\"]")));

        List<GscClient.GscRow> result = client.query(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14),
                List.of("date", "page"));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).keys()).containsExactly("2026-10-14", "https://example.test/a");
        assertThat(result.get(0).clicks()).isEqualTo(3);
        assertThat(result.get(0).impressions()).isEqualTo(40);
        assertThat(result.get(0).position()).isEqualTo(7.5);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        List<HttpRequest> requests = captor.getAllValues();

        HttpRequest tokenRequest = requests.get(0);
        assertThat(tokenRequest.uri().toString()).isEqualTo(TOKEN_URI);
        String form = body(tokenRequest);
        String assertion = URLDecoder.decode(form.substring(form.indexOf("assertion=") + 10), StandardCharsets.UTF_8);
        Claims claims = Jwts.parser().verifyWith(keyPair.getPublic()).clock(() -> java.util.Date.from(CLOCK.instant())).build().parseSignedClaims(assertion).getPayload();
        assertThat(claims.getIssuer()).isEqualTo("reader@example.iam.test");
        assertThat(claims.getAudience()).containsExactly(TOKEN_URI);
        // Google verlangt aud als String, nicht als Array
        String payloadJson = new String(Base64.getUrlDecoder().decode(assertion.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payloadJson).contains("\"aud\":\"" + TOKEN_URI + "\"");
        assertThat(claims.get("scope", String.class)).isEqualTo("https://www.googleapis.com/auth/webmasters.readonly");

        HttpRequest first = requests.get(1);
        assertThat(first.uri().toString())
                .isEqualTo("https://searchconsole.googleapis.com/webmasters/v3/sites/sc-domain%3Aexample.test/searchAnalytics/query");
        assertThat(first.headers().firstValue("Authorization")).contains("Bearer tok-1");
        assertThat(body(first)).contains("\"startRow\":0").contains("\"rowLimit\":2").contains("\"dimensions\":[\"date\",\"page\"]");
        assertThat(body(requests.get(2))).contains("\"startRow\":2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reusesTokenAndFailsLoudOnHttpError() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                response(200, "{\"access_token\":\"tok-1\",\"expires_in\":3600}"),
                response(200, rows()),
                response(403, "{\"error\":\"forbidden\"}"));

        assertThat(client.query(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14), List.of("date"))).isEmpty();
        assertThatThrownBy(() -> client.query(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15), List.of("date")))
                .isInstanceOf(GscClientException.class)
                .hasMessageContaining("403");
        verify(http, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static String rows(String... keyArrays) {
        StringBuilder sb = new StringBuilder("{\"rows\":[");
        for (int i = 0; i < keyArrays.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"keys\":").append(keyArrays[i]).append(",\"clicks\":3,\"impressions\":40,\"ctr\":0.075,\"position\":7.5}");
        }
        return sb.append("]}").toString();
    }

    /** Echte Implementierung statt Mock: Mocks innerhalb von thenReturn(...) bricht Mockito ab. */
    private static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return null; }
            @Override public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
            @Override public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
            @Override public java.net.URI uri() { return null; }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    /** Liest den Request-Body aus dem BodyPublisher (synchron, Test-Hilfe). */
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
