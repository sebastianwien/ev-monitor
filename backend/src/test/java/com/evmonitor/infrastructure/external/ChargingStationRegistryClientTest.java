package com.evmonitor.infrastructure.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Der Client fragt das Ladesaeulenregister im Umkreis eines Punktes ab.
 * Geprueft werden der Aufbau der Anfrage und die Robustheit der Antwortauswertung -
 * der Dienst ist fremd, jede Abweichung darf nur zu einer leeren Liste fuehren.
 */
class ChargingStationRegistryClientTest {

    private RestTemplate restTemplate;
    private ChargingStationRegistryClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new ChargingStationRegistryClient(restTemplate, true);
    }

    private void respondWith(Map<String, Object>... attributes) {
        List<Map<String, Object>> features = java.util.Arrays.stream(attributes)
                .map(a -> Map.<String, Object>of("attributes", a))
                .toList();
        when(restTemplate.getForObject(any(URI.class), eq(Map.class)))
                .thenReturn(Map.of("features", features));
    }

    @Test
    void liefertBetreiberUndMarkeAusDerAntwort() {
        respondWith(Map.of("Betreiber", "Allego GmbH", "Anzeigename__Karte_", "Allego"));

        var stations = client.findStationsNearby(52.52, 13.405, 250).orElseThrow();

        assertThat(stations).hasSize(1);
        assertThat(stations.getFirst().operator()).isEqualTo("Allego GmbH");
        assertThat(stations.getFirst().brand()).isEqualTo("Allego");
    }

    /** 56 % der DC-Eintraege im Register fuehren keinen Anzeigenamen - der Betreiber traegt allein. */
    @Test
    void fehlendeMarkeIstKeinFehler() {
        respondWith(Map.of("Betreiber", "EnBW mobility+ AG und Co.KG "));

        var stations = client.findStationsNearby(52.52, 13.405, 250).orElseThrow();

        assertThat(stations).hasSize(1);
        assertThat(stations.getFirst().operator()).isEqualTo("EnBW mobility+ AG und Co.KG");
        assertThat(stations.getFirst().brand()).isNull();
    }

    /** Fuer die Standortvorschlaege im Log-Formular braucht es Position, Leistung und Ladeart. */
    @Test
    void liestPositionLeistungUndLadeartAus() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("Betreiber", "IONITY GmbH");
        row.put("Anzeigename__Karte_", "IONITY");
        row.put("Breitengrad", 49.45);
        row.put("Längengrad", 11.05432);
        row.put("Nennleistung_Ladeeinrichtung__kW_", 350);
        row.put("Art_der_Ladeeinrichtung", "Schnellladeeinrichtung");
        row.put("Anzahl_Ladepunkte", 6);
        respondWith(row);

        var station = client.findStationsNearby(49.45, 11.05, 250).orElseThrow().getFirst();

        assertThat(station.latitude()).isEqualTo(49.45);
        assertThat(station.longitude()).isEqualTo(11.05432);
        assertThat(station.powerKw()).isEqualTo(350.0);
        assertThat(station.fastCharging()).isTrue();
        assertThat(station.chargePoints()).isEqualTo(6);
    }

    /** Das Register fuehrt nicht jede Angabe - fehlende Zusatzfelder machen den Eintrag nicht unbrauchbar. */
    @Test
    void fehlendeZusatzfelderSindKeinFehler() {
        respondWith(Map.of("Betreiber", "Stadtwerke X", "Art_der_Ladeeinrichtung", "Normalladeeinrichtung"));

        var station = client.findStationsNearby(49.45, 11.05, 250).orElseThrow().getFirst();

        assertThat(station.latitude()).isNull();
        assertThat(station.powerKw()).isNull();
        assertThat(station.fastCharging()).isFalse();
        assertThat(station.chargePoints()).isNull();
    }

    @Test
    void eintraegeOhneBetreiberWerdenUebersprungen() {
        respondWith(Map.of("Anzeigename__Karte_", "Irgendwas"), Map.of("Betreiber", "Allego GmbH"));

        var stations = client.findStationsNearby(52.52, 13.405, 250).orElseThrow();

        assertThat(stations).extracting(ChargingStationRegistryClient.Station::operator)
                .containsExactly("Allego GmbH");
    }

    @Test
    void baustDieUmkreisanfrageMitKoordinatenUndRadius() {
        respondWith();

        client.findStationsNearby(52.52, 13.405, 250);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uri.capture(), eq(Map.class));
        String query = java.net.URLDecoder.decode(uri.getValue().toString(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(query).contains("\"x\":13.405").contains("\"y\":52.52");
        assertThat(query).contains("distance=250").contains("units=esriSRUnit_Meter");
        assertThat(query).contains("returnGeometry=false");
        assertThat(query).contains("Breitengrad").contains("Längengrad")
                .contains("Nennleistung_Ladeeinrichtung__kW_").contains("Art_der_Ladeeinrichtung")
                .contains("Anzahl_Ladepunkte");
        // jede Ladeeinrichtung einzeln: nur so lassen sich Ladepunkte je Standort zusammenzaehlen
        assertThat(query).doesNotContain("returnDistinctValues=true");
    }

    /**
     * Ein Ausfall ist etwas anderes als "hier steht nichts": nur so kann der Aufrufer
     * die Antwort cachen, ohne einen kurzen Ausfall dreissig Tage lang festzuhalten.
     */
    @Test
    void fehlerDesDienstesIstVomLeerenErgebnisUnterscheidbar() {
        when(restTemplate.getForObject(any(URI.class), eq(Map.class)))
                .thenThrow(new RestClientException("timeout"));

        assertThat(client.findStationsNearby(52.52, 13.405, 250)).isEmpty();
    }

    @Test
    void antwortOhneStationenIstEinGueltigesErgebnis() {
        respondWith();

        assertThat(client.findStationsNearby(52.52, 13.405, 250)).isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    void unerwarteteAntwortGiltAlsAusfall() {
        when(restTemplate.getForObject(any(URI.class), eq(Map.class)))
                .thenReturn(Map.of("error", Map.of("code", 499)));

        assertThat(client.findStationsNearby(52.52, 13.405, 250)).isEmpty();
    }

    /** Abschaltbar ohne Deploy: kein Aufruf, kein Fehler. */
    @Test
    void abgeschalteterClientFragtNichtAn() {
        ChargingStationRegistryClient disabled = new ChargingStationRegistryClient(restTemplate, false);

        assertThat(disabled.findStationsNearby(52.52, 13.405, 250)).isEmpty();
        verifyNoInteractions(restTemplate);
    }
}
