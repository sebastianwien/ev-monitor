package com.evmonitor.infrastructure.external.dataforseo;

import com.evmonitor.application.seo.SeoCompetitionProperties;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.KeywordData;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.Paid;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.SerpItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Erfundene Begriffe und Domains im Antwortformat der DataForSEO-API v3; HttpClient gemockt. */
class DataForSeoClientTest {

    private final HttpClient http = mock(HttpClient.class);
    private final SeoCompetitionProperties properties = new SeoCompetitionProperties();
    private final DataForSeoClient client;

    DataForSeoClientTest() {
        properties.setLogin("user@example.test");
        properties.setPassword("geheim");
        client = new DataForSeoClient(properties, new ObjectMapper(), http);
    }

    @SuppressWarnings("unchecked")
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private HttpRequest sent() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        return captor.getValue();
    }

    @Test
    void disabledWithoutCredentials() {
        properties.setPassword(" ");
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void serpReturnsOnlyOrganicItemsWithCostAndSendsBasicAuthAndGermanMobileTask() throws Exception {
        respond(200, """
                {"status_code":20000,"cost":0.002,"tasks":[{"status_code":20000,"status_message":"Ok.","cost":0.002,"result":[{"items":[
                  {"type":"paid","rank_group":1,"domain":"anzeige.test","url":"https://anzeige.test/","title":"Anzeige"},
                  {"type":"organic","rank_group":1,"domain":"www.wettbewerb.test","url":"https://www.wettbewerb.test/a","title":"Testauto Reichweite"},
                  {"type":"featured_snippet","rank_group":1,"domain":"snippet.test","url":"https://snippet.test/","title":"x"},
                  {"type":"organic","rank_group":2,"domain":"example.test","url":"https://example.test/modelle/A","title":null}
                ]}]}]}
                """);

        Paid<List<SerpItem>> result = client.serp("testauto reichweite");

        assertThat(result.costUsd()).isEqualTo(0.002);
        assertThat(result.value()).containsExactly(
                new SerpItem(1, "wettbewerb.test", "https://www.wettbewerb.test/a", "Testauto Reichweite"),
                new SerpItem(2, "example.test", "https://example.test/modelle/A", null));
        HttpRequest req = sent();
        assertThat(req.uri().toString()).isEqualTo("https://api.dataforseo.com/v3/serp/google/organic/live/regular");
        String auth = Base64.getEncoder().encodeToString("user@example.test:geheim".getBytes(StandardCharsets.UTF_8));
        assertThat(req.headers().firstValue("Authorization")).contains("Basic " + auth);
        assertThat(body(req)).contains("\"keyword\":\"testauto reichweite\"").contains("\"location_code\":2276")
                .contains("\"language_code\":\"de\"").contains("\"device\":\"mobile\"").contains("\"depth\":10");
    }

    @Test
    void searchVolumeReadsVolumeCpcAndCompetition() throws Exception {
        respond(200, """
                {"status_code":20000,"cost":0.075,"tasks":[{"status_code":20000,"cost":0.075,"result":[
                  {"keyword":"testauto reichweite","search_volume":1300,"cpc":0.84,"competition":"LOW"},
                  {"keyword":"testauto verbrauch","search_volume":null,"cpc":null,"competition":null}
                ]}]}
                """);

        Paid<List<KeywordData>> result = client.searchVolume(List.of("testauto reichweite", "testauto verbrauch"));

        assertThat(result.costUsd()).isEqualTo(0.075);
        assertThat(result.value()).containsExactly(
                new KeywordData("testauto reichweite", 1300, 0.84, "LOW"),
                new KeywordData("testauto verbrauch", null, null, null));
        assertThat(sent().uri().toString()).endsWith("/v3/keywords_data/google_ads/search_volume/live");
    }

    @Test
    void suggestionsReadKeywordInfo() throws Exception {
        respond(200, """
                {"status_code":20000,"cost":0.018,"tasks":[{"status_code":20000,"cost":0.018,"result":[{"items":[
                  {"keyword":"testauto reichweite winter","keyword_info":{"search_volume":210,"cpc":1.2,"competition_level":"MEDIUM"}},
                  {"keyword":"testauto akku","keyword_info":{"search_volume":90}}
                ]}]}]}
                """);

        Paid<List<KeywordData>> result = client.suggestions("testauto reichweite", 50);

        assertThat(result.value()).containsExactly(
                new KeywordData("testauto reichweite winter", 210, 1.2, "MEDIUM"),
                new KeywordData("testauto akku", 90, null, null));
        HttpRequest req = sent();
        assertThat(req.uri().toString()).endsWith("/v3/dataforseo_labs/google/keyword_suggestions/live");
        assertThat(body(req)).contains("\"limit\":50");
    }

    @Test
    void apiErrorThrowsWithoutCredentialsInMessage() throws Exception {
        respond(200, "{\"status_code\":20000,\"tasks\":[{\"status_code\":40200,\"status_message\":\"Payment Required.\",\"cost\":0}]}");
        assertThatThrownBy(() -> client.serp("x")).isInstanceOf(DataForSeoException.class)
                .hasMessageContaining("40200").hasMessageContaining("Payment Required").hasMessageNotContaining("geheim");

        respond(401, "{\"status_code\":40100,\"status_message\":\"Unauthorized.\"}");
        assertThatThrownBy(() -> client.serp("x")).isInstanceOf(DataForSeoException.class).hasMessageContaining("401");
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
