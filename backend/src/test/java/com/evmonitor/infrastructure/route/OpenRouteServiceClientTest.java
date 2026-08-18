package com.evmonitor.infrastructure.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
                .thenReturn(Map.of("routes", List.of(Map.of("geometry", "e`p_IouspA}@Z"))));

        Optional<String> result = clientWithKey("key").route(52.5, 13.4, 52.4, 13.5);

        assertThat(result).contains("e`p_IouspA}@Z");
    }

    @Test
    void ohneApiKeyWirdNichtAufgerufen() {
        Optional<String> result = clientWithKey("  ").route(52.5, 13.4, 52.4, 13.5);

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
