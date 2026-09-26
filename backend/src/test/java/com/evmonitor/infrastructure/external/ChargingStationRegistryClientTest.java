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
        assertThat(station.maxDcKw()).isEqualTo(350.0);
        assertThat(station.fastCharging()).isTrue();
        assertThat(station.chargePoints()).isEqualTo(6);
    }

    /**
     * "Nennleistung Ladeeinrichtung" ist die Summe aller Stecker (Kaufland Berlin: 43 AC + 50 DC = 93)
     * und fuer den Fahrer wertlos. Gebraucht wird die Leistung je Ladeart aus den Steckerfeldern,
     * dazu Register-ID und Adresse, damit ein Standort wiedererkennbar und unterscheidbar ist.
     */
    @Test
    void liestSteckerAdresseUndRegisterIdAus() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("Ladeeinrichtungs_ID", 1064145);
        row.put("Betreiber", "Kaufland Dienstleistung GmbH & Co. KG");
        row.put("Anzeigename__Karte_", "Kaufland");
        row.put("Status", "In Betrieb");
        row.put("Art_der_Ladeeinrichtung", "Schnellladeeinrichtung");
        row.put("Anzahl_Ladepunkte", 2);
        row.put("Nennleistung_Ladeeinrichtung__kW_", 93);
        row.put("Inbetriebnahmedatum", 1543273200000L);
        row.put("Straße", "Storkower Str.");
        row.put("Hausnummer", "139");
        row.put("Postleitzahl", "10407");
        row.put("Ort", "Berlin");
        row.put("Standortbezeichnung", "3330_Kland_Berlin");
        row.put("Bezahlsysteme", "Onlinezahlungsverfahren");
        row.put("Öffnungszeiten", "24/7");
        row.put("Steckertypen1", "AC Typ 2 Fahrzeugkupplung");
        row.put("Nennleistung_Stecker1", "43");
        row.put("Steckertypen2", "DC Fahrzeugkupplung Typ Combo 2 (CCS); DC CHAdeMO");
        row.put("Nennleistung_Stecker2", "50; 50");
        respondWith(row);

        var s = client.findStationsNearby(52.53, 13.45, 250).orElseThrow().getFirst();

        assertThat(s.registerId()).isEqualTo(1064145);
        assertThat(s.maxAcKw()).isEqualTo(43.0);
        assertThat(s.maxDcKw()).isEqualTo(50.0);
        assertThat(s.fastCharging()).isTrue();
        assertThat(s.plugTypes()).containsExactly("Typ 2", "CCS", "CHAdeMO");
        assertThat(s.street()).isEqualTo("Storkower Str.");
        assertThat(s.houseNumber()).isEqualTo("139");
        assertThat(s.postalCode()).isEqualTo("10407");
        assertThat(s.city()).isEqualTo("Berlin");
        assertThat(s.commissionedOn()).isEqualTo(java.time.LocalDate.of(2018, 11, 27));
        assertThat(s.siteLabel()).isEqualTo("3330_Kland_Berlin");
        assertThat(s.payment()).isEqualTo("Onlinezahlungsverfahren");
        assertThat(s.openingHours()).isEqualTo("24/7");
    }

    /** Ohne Steckerangaben entscheidet die Art der Ladeeinrichtung ueber DC. */
    @Test
    void ohneSteckerZaehltDieArtDerLadeeinrichtung() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("Betreiber", "IONITY GmbH");
        row.put("Art_der_Ladeeinrichtung", "Schnellladeeinrichtung");
        row.put("Nennleistung_Ladeeinrichtung__kW_", 350);
        respondWith(row);

        var s = client.findStationsNearby(49.45, 11.05, 250).orElseThrow().getFirst();

        assertThat(s.fastCharging()).isTrue();
        assertThat(s.maxDcKw()).isEqualTo(350.0);
        assertThat(s.maxAcKw()).isNull();
    }

    /** Ausser Betrieb gemeldete Einrichtungen sind kein Ladeort. */
    @Test
    void laesstEinrichtungenAusserBetriebWeg() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("Betreiber", "IONITY GmbH");
        row.put("Status", "Außer Betrieb");
        respondWith(row);

        assertThat(client.findStationsNearby(49.45, 11.05, 250).orElseThrow()).isEmpty();
    }

    /** Das Register fuehrt nicht jede Angabe - fehlende Zusatzfelder machen den Eintrag nicht unbrauchbar. */
    @Test
    void fehlendeZusatzfelderSindKeinFehler() {
        respondWith(Map.of("Betreiber", "Stadtwerke X", "Art_der_Ladeeinrichtung", "Normalladeeinrichtung"));

        var station = client.findStationsNearby(49.45, 11.05, 250).orElseThrow().getFirst();

        assertThat(station.latitude()).isNull();
        assertThat(station.maxAcKw()).isNull();
        assertThat(station.maxDcKw()).isNull();
        assertThat(station.fastCharging()).isFalse();
        assertThat(station.chargePoints()).isNull();
    }

    /** Privatpersonen tragen sich mit geschuetzten Leerzeichen und Doppelspaces ein. */
    @Test
    void normalisiertWhitespaceInNamen() {
        respondWith(Map.of("Betreiber", "Norman Roger Martin\u00a0 Hesse", "Anzeigename__Karte_", "Frank  Höhn "));

        var station = client.findStationsNearby(49.45, 11.05, 250).orElseThrow().getFirst();

        assertThat(station.operator()).isEqualTo("Norman Roger Martin Hesse");
        assertThat(station.brand()).isEqualTo("Frank Höhn");
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
                .contains("Anzahl_Ladepunkte").contains("Ladeeinrichtungs_ID").contains("Steckertypen1")
                .contains("Nennleistung_Stecker6").contains("Status").contains("Straße");
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

    // ── Textsuche ────────────────────────────────────────────────────────────────

    private String capturedQuery() {
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uri.capture(), eq(Map.class));
        return java.net.URLDecoder.decode(uri.getValue().toString(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Jeder Begriff muss irgendwo treffen: Betreiber, Anzeigename, Ort oder Strasse. */
    @Test
    void textsucheVerknuepftBegriffeMitUndUeberAlleNamensfelder() {
        respondWith();

        client.searchStations("EnBW Lichtenau Fuchs");

        String q = capturedQuery();
        assertThat(q).contains("UPPER(Betreiber) LIKE '%ENBW%'")
                .contains("UPPER(Anzeigename__Karte_) LIKE '%ENBW%'")
                .contains("UPPER(Ort) LIKE '%LICHTENAU%'")
                .contains("UPPER(Straße) LIKE '%FUCHS%'");
        assertThat(q.split(" AND ")).hasSize(3);
        assertThat(q).doesNotContain("geometry=").contains("returnGeometry=false");
    }

    /** Zahlen sind Postleitzahl oder Hausnummer, nie Teil eines Namens. */
    @Test
    void zahlenSuchenPostleitzahlUndHausnummer() {
        respondWith();

        client.searchStations("91586 Lichtenau");

        assertThat(capturedQuery()).contains("(Postleitzahl LIKE '91586%' OR Hausnummer = '91586')")
                .doesNotContain("LIKE '%91586%'");
    }

    /** Der Suchtext landet in einer Where-Klausel eines fremden Dienstes: Quotes und Wildcards entschaerfen. */
    @Test
    void textsucheEntschaerftQuotesUndWildcards() {
        respondWith();

        client.searchStations("O'Neil %Stadt_");

        String q = capturedQuery();
        assertThat(q).contains("'%O''NEIL%'").contains("'%STADT%'").doesNotContain("%%").doesNotContain("_%'");
    }

    /** Ein Buchstabe trifft alles und bringt nichts; mehr als fuenf Begriffe sind kein Suchtext mehr. */
    @Test
    void textsucheIgnoriertKurzeUndUeberzaehligeBegriffe() {
        respondWith();

        client.searchStations("a EnBW, bb cc dd ee ff gg");

        String q = capturedQuery();
        assertThat(q).doesNotContain("'%A%'").doesNotContain("'%FF%'");
        assertThat(q.split(" AND ")).hasSize(5);
    }

    @Test
    void textsucheOhneBrauchbarenBegriffFragtNichtAn() {
        assertThat(client.searchStations("a 1")).isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void textsucheLiefertDieselbenStationenWieDieUmkreissuche() {
        respondWith(Map.of("Betreiber", "EnBW mobility+ AG und Co.KG ", "Ort", "Lichtenau", "Status", "In Betrieb"),
                Map.of("Betreiber", "N-ERGIE", "Status", "Außer Betrieb"));

        var stations = client.searchStations("EnBW Lichtenau").orElseThrow();

        assertThat(stations).extracting(ChargingStationRegistryClient.Station::operator)
                .containsExactly("EnBW mobility+ AG und Co.KG");
        assertThat(stations.getFirst().city()).isEqualTo("Lichtenau");
    }

    @Test
    void textsucheBeiAusfallIstLeeresOptional() {
        when(restTemplate.getForObject(any(URI.class), eq(Map.class)))
                .thenThrow(new RestClientException("timeout"));

        assertThat(client.searchStations("EnBW Lichtenau")).isEmpty();
    }
}
