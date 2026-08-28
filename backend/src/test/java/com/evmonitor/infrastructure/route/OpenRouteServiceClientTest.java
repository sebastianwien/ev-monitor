package com.evmonitor.infrastructure.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class OpenRouteServiceClientTest {

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
    }

    private OpenRouteServiceClient clientWithKey(String key) {
        return new OpenRouteServiceClient(restTemplate, key);
    }

    @Test
    void liefertPolylineAusDerAntwort() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("routes", List.of(Map.of(
                        "geometry", "e`p_IouspA}@Z",
                        "summary", Map.of("distance", 12345.6)))));

        Optional<OpenRouteServiceClient.Route> result = clientWithKey("key").route(52.5, 13.4, 52.4, 13.5);

        assertThat(result).isPresent();
        assertThat(result.get().polyline()).isEqualTo("e`p_IouspA}@Z");
        assertThat(result.get().distanceMeters()).isEqualTo(12345.6);
    }

    /** Ohne summary bleibt die Laenge 0 - der Aufrufer erkennt daran, dass er nichts pruefen kann. */
    @Test
    void antwortOhneLaengenangabeLiefertNull() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("routes", List.of(Map.of("geometry", "e`p_IouspA}@Z"))));

        Optional<OpenRouteServiceClient.Route> result = clientWithKey("key").route(52.5, 13.4, 52.4, 13.5);

        assertThat(result).isPresent();
        assertThat(result.get().distanceMeters()).isZero();
    }

    /**
     * ORS erwartet Laenge vor Breite. Vertauscht landet die Fahrt auf der anderen Erdhalbkugel,
     * ohne dass irgendetwas fehlschlaegt - deshalb steht die Reihenfolge hier fest.
     */
    @Test
    void schicktKoordinatenAlsLaengeVorBreite() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("routes", List.of(Map.of("geometry", "x", "summary", Map.of("distance", 1.0)))));

        clientWithKey("key").route(52.5, 13.4, 52.4, 13.5);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), request.capture(), eq(Map.class));
        List<List<Double>> coordinates =
                (List<List<Double>>) request.getValue().getBody().get("coordinates");
        assertThat(coordinates).containsExactly(List.of(13.4, 52.5), List.of(13.5, 52.4));
    }

    @Test
    void mehrAlsFuenfzigWegpunkteFragtDerDienstGarNichtErstAb() {
        List<double[]> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= OpenRouteServiceClient.MAX_WAYPOINTS; i++) {
            tooMany.add(new double[]{52.5 + i * 0.001, 13.4});
        }

        assertThat(clientWithKey("key").route(tooMany)).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void eineinzelnerPunktIstKeineRoute() {
        assertThat(clientWithKey("key").route(List.of(new double[]{52.5, 13.4}))).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void ohneApiKeyWirdNichtAufgerufen() {
        Optional<OpenRouteServiceClient.Route> result = clientWithKey("  ").route(52.5, 13.4, 52.4, 13.5);

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void fehlerDesRoutersBleibtFolgenlos() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("timeout"));

        assertThat(clientWithKey("key").route(52.5, 13.4, 52.4, 13.5)).isEmpty();
    }

    @Test
    void antwortOhneRouteBleibtFolgenlos() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("routes", List.of()));

        assertThat(clientWithKey("key").route(52.5, 13.4, 52.4, 13.5)).isEmpty();
    }
}
