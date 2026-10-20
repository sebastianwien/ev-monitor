package com.evmonitor.infrastructure.external.plausible;

import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Query;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Row;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Erfundene Site und Pfade; HttpClient gemockt. */
class PlausibleClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final PlausibleClient client = new PlausibleClient("key-1", "example.test", objectMapper, http, 2);

    @Test
    void disabledWithoutKey() {
        assertThat(new PlausibleClient("  ", "example.test", objectMapper).isEnabled()).isFalse();
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsBearerBodyWithGoalFilterAndPaginatesUntilShortPage() throws Exception {
        HttpResponse<String> page1 = response(200, results("[\"2026-10-14\",\"/a\"],\"metrics\":[5,7]", "[\"2026-10-14\",\"/b\"],\"metrics\":[1,null]"));
        HttpResponse<String> page2 = response(200, results("[\"2026-10-15\",\"/a\"],\"metrics\":[2,3]"));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(page1, page2);

        List<Row> rows = client.query(new Query(List.of("visitors", "pageviews"), LocalDate.of(2026, 10, 14),
                LocalDate.of(2026, 10, 15), List.of("time:day", "event:page"), List.of("login")));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).dim(1)).isEqualTo("/a");
        assertThat(rows.get(0).metric(1)).isEqualTo(7.0);
        assertThat(rows.get(1).metric(1)).isZero();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest first = captor.getAllValues().get(0);
        assertThat(first.uri().toString()).isEqualTo("https://plausible.io/api/v2/query");
        assertThat(first.headers().firstValue("Authorization")).contains("Bearer key-1");
        assertThat(body(first))
                .contains("\"site_id\":\"example.test\"")
                .contains("\"date_range\":[\"2026-10-14\",\"2026-10-15\"]")
                .contains("\"dimensions\":[\"time:day\",\"event:page\"]")
                .contains("\"filters\":[[\"is\",\"event:goal\",[\"login\"]]]")
                .contains("\"limit\":2").contains("\"offset\":0");
        assertThat(body(captor.getAllValues().get(1))).contains("\"offset\":2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void noFilterWithoutGoalsAndErrorsOnNon200() throws Exception {
        HttpResponse<String> empty = response(200, results());
        HttpResponse<String> limited = response(429, "{\"error\":\"too many\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(empty, limited);
        Query q = new Query(List.of("visitors"), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 14), List.of("time:day"), null);

        assertThat(client.query(q)).isEmpty();
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(body(captor.getValue())).doesNotContain("filters");

        assertThatThrownBy(() -> client.query(q)).isInstanceOf(PlausibleClientException.class).hasMessageContaining("429");
    }

    private static String results(String... rows) {
        StringBuilder sb = new StringBuilder("{\"results\":[");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"dimensions\":").append(rows[i]).append('}');
        }
        return sb.append("],\"meta\":{}}").toString();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(body);
        return r;
    }

    private static String body(HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) { sb.append(StandardCharsets.UTF_8.decode(item)); }
            @Override public void onError(Throwable t) { }
            @Override public void onComplete() { }
        });
        return sb.toString();
    }
}
