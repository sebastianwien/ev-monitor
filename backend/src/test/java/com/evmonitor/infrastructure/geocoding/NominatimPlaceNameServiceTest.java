package com.evmonitor.infrastructure.geocoding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Der Ortsname fuer eine Zelle ohne Saeule kommt von Nominatim - einmal je Zelle, danach aus
 * dem Cache. Gefragt wird nur die Zellmitte, nie eine Nutzerposition. Antwortet der Dienst
 * nicht, bleibt der Ort ohne Namen und wird beim naechsten Mal erneut gefragt.
 */
class NominatimPlaceNameServiceTest {

    private RestTemplate http;
    private NominatimPlaceNameService service;

    @BeforeEach
    void setUp() {
        http = mock(RestTemplate.class);
        service = new NominatimPlaceNameService(http, "https://nominatim.test", "ev-monitor-test", true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void answer(Map<String, Object> address) {
        when(http.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(Map.of("address", address)));
    }

    @Test
    void nimmtDenOrtsteilVorDorfUndStadt() {
        answer(Map.of("suburb", "Prenzlauer Berg", "city", "Berlin"));
        assertThat(service.nameFor("u33dc0")).contains("Prenzlauer Berg");
    }

    @Test
    void laendlichDorfOderWeilerVorGemeinde() {
        answer(Map.of("hamlet", "Oberdorf", "municipality", "Feuchtwangen"));
        assertThat(service.nameFor("u0zpr5")).contains("Oberdorf");

        answer(Map.of("village", "Dorfkemmathen", "county", "Ansbach"));
        assertThat(service.nameFor("u0zpr6")).contains("Dorfkemmathen");
    }

    @Test
    void stadtUndGemeindeAlsLetzteStufen() {
        answer(Map.of("town", "Dinkelsbühl"));
        assertThat(service.nameFor("u0zpr7")).contains("Dinkelsbühl");

        answer(Map.of("municipality", "Schopfloch"));
        assertThat(service.nameFor("u0zpr8")).contains("Schopfloch");
    }

    @Test
    void fragtDieZellmitteNichtEineNutzerposition() {
        answer(Map.of("city", "Berlin"));
        service.nameFor("u33dc0c");

        var uri = org.mockito.ArgumentCaptor.forClass(URI.class);
        var entity = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).exchange(uri.capture(), eq(HttpMethod.GET), entity.capture(), eq(Map.class));
        assertThat(entity.getValue().getHeaders().getFirst("User-Agent")).isEqualTo("ev-monitor-test");
        // Mitte von u33dc0c: 52.5195 / 13.4054 - gerundet auf 4 Stellen
        assertThat(uri.getValue().toString()).contains("lat=52.519").contains("lon=13.405").contains("zoom=14");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cachtTrefferAberKeineFehlschlaege() {
        when(http.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn((ResponseEntity) ResponseEntity.ok(Map.of("address", Map.of("city", "Berlin"))));

        assertThat(service.nameFor("u33dc0")).isEmpty();
        assertThat(service.nameFor("u33dc0")).contains("Berlin");
        assertThat(service.nameFor("u33dc0")).contains("Berlin");
        verify(http, times(2)).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void ohneBrauchbareEbeneKeinName() {
        answer(Map.of("country", "Deutschland"));
        assertThat(service.nameFor("u33dc0")).isEmpty();
    }

    @Test
    void abgeschaltetFragtNichts() {
        var off = new NominatimPlaceNameService(http, "https://nominatim.test", "ev-monitor-test", false);
        assertThat(off.nameFor("u33dc0")).isEmpty();
        verifyNoInteractions(http);
    }
}
