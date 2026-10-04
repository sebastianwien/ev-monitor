package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingProviderTariffService;
import com.evmonitor.application.NearbyCpoService;
import com.evmonitor.application.NearbyStation;
import com.evmonitor.application.StationMatch;
import com.evmonitor.application.StationSearchService;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Der Umkreis-Endpunkt reicht Koordinaten an einen fremden Dienst weiter.
 * Geprueft werden deshalb Eingabegrenzen, Drosselung und die Umwandlung in eine
 * Geohash-Zelle - die Rohkoordinaten duerfen den Server nicht verlassen.
 */
class ChargingProviderTariffControllerNearbyTest {

    private NearbyCpoService nearbyCpoService;
    private StationSearchService stationSearchService;
    private RateLimitService rateLimitService;
    private ChargingProviderTariffController controller;
    private Authentication request;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        nearbyCpoService = mock(NearbyCpoService.class);
        stationSearchService = mock(StationSearchService.class);
        rateLimitService = mock(RateLimitService.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        request = mock(Authentication.class);
        when(request.getPrincipal()).thenReturn(UserPrincipal.create(user));
        when(rateLimitService.tryConsumeCpoLookup(anyString())).thenReturn(true);
        when(rateLimitService.tryConsumeStationSearch(anyString())).thenReturn(true);
        controller = new ChargingProviderTariffController(
                mock(ChargingProviderTariffService.class), nearbyCpoService, stationSearchService, rateLimitService);
    }

    @Test
    void reichtNurDieGeohashZelleWeiterNichtDieKoordinaten() {
        when(nearbyCpoService.findNearbyCpos(anyString())).thenReturn(Optional.of(List.of("EnBW")));

        ResponseEntity<?> response = controller.getNearbyCpos(52.520008, 13.404954, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(List.of("EnBW"));
        // sieben Stellen entsprechen der Praezision, die oeffentliche Ladungen ohnehin speichern
        verify(nearbyCpoService).findNearbyCpos("u33dc0c");
    }

    @Test
    void keineTrefferSindEineLeereListe() {
        when(nearbyCpoService.findNearbyCpos(anyString())).thenReturn(Optional.of(List.of()));

        ResponseEntity<?> response = controller.getNearbyCpos(52.52, 13.40, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(List.of());
    }

    /** Faellt das Register aus, sieht das Formular dasselbe wie bei "kein Vorschlag". */
    @Test
    void ausfallDesRegistersBlockiertDasFormularNicht() {
        when(nearbyCpoService.findNearbyCpos(anyString())).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getNearbyCpos(52.52, 13.40, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(List.of());
    }

    @Test
    void koordinatenAusserhalbDerErdeWerdenAbgelehnt() {
        assertThat(controller.getNearbyCpos(91.0, 13.4, request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.getNearbyCpos(52.5, 181.0, request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.getNearbyCpos(Double.NaN, 13.4, request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(nearbyCpoService);
    }

    /** Ohne Drosselung waere der Endpunkt ein kostenloser Proxy auf das Register. */
    @Test
    void ueberschritteneDrosselungLiefert429() {
        when(rateLimitService.tryConsumeCpoLookup(anyString())).thenReturn(false);

        ResponseEntity<?> response = controller.getNearbyCpos(52.52, 13.40, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(nearbyCpoService, never()).findNearbyCpos(anyString());
    }

    /** Ein Topf je Nutzer, geteilt mit dem Speichern eines Logs mit Standort (ChargingSiteService). */
    @Test
    void drosselungZaehltProNutzer() {
        when(nearbyCpoService.findNearbyCpos(anyString())).thenReturn(Optional.of(List.of()));

        controller.getNearbyCpos(52.52, 13.40, request);

        verify(rateLimitService).tryConsumeCpoLookup("user:" + userId);
    }

    // --- Standortvorschlaege ---

    @Test
    void standorteReichenEbenfallsNurDieGeohashZelleWeiter() {
        var station = new NearbyStation("IONITY", true, 40, 350.0, true, 6, "u33dc0c");
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(station)));

        ResponseEntity<?> response = controller.getNearbyStations(52.520008, 13.404954, null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(List.of(station));
        verify(nearbyCpoService).findNearbyStations("u33dc0c");
    }

    /** "Umkreis erweitern": mit Radius laeuft die weite Suche, ohne Radius die gecachte enge. */
    @Test
    void radiusSchaltetAufDieWeiteSucheUm() {
        when(nearbyCpoService.findNearbyStations(anyString(), anyInt())).thenReturn(Optional.of(List.of()));

        controller.getNearbyStations(52.520008, 13.404954, 2_500, request);

        verify(nearbyCpoService).findNearbyStations("u33dc0c", 2_500);
        verify(nearbyCpoService, never()).findNearbyStations(anyString());
    }

    @Test
    void ausfallDesRegistersLiefertBeiStandortenEineLeereListe() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());

        assertThat(controller.getNearbyStations(52.52, 13.40, null, request).getBody()).isEqualTo(List.of());
    }

    @Test
    void standortabfrageIstGedrosseltUndGeprueft() {
        assertThat(controller.getNearbyStations(91.0, 13.4, null, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        when(rateLimitService.tryConsumeCpoLookup(anyString())).thenReturn(false);
        assertThat(controller.getNearbyStations(52.52, 13.40, null, request).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(nearbyCpoService, never()).findNearbyStations(anyString());
        verify(nearbyCpoService, never()).findNearbyStations(anyString(), anyInt());
    }

    // ── Textsuche ────────────────────────────────────────────────────────────────

    /** Gleiche Eingabe in anderer Schreibweise ist derselbe Cache-Eintrag und dieselbe Registeranfrage. */
    @Test
    void textsucheNormalisiertDieEingabe() {
        when(stationSearchService.search(anyString())).thenReturn(Optional.of(List.of()));

        controller.searchStations("  EnBW   Lichtenau ", request);

        verify(stationSearchService).search("enbw lichtenau");
    }

    @Test
    void textsucheLehntZuKurzeUndZuLangeEingabenAb() {
        assertThat(controller.searchStations("En", request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.searchStations("x".repeat(81), request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(stationSearchService);
    }

    /** Autocomplete tippt viele Anfragen: eigener Topf, damit nicht die 60 Umkreisabfragen leerlaufen. */
    @Test
    void textsucheNutztEigenenDrosselTopf() {
        when(rateLimitService.tryConsumeStationSearch("user:" + userId)).thenReturn(false);

        assertThat(controller.searchStations("EnBW Lichtenau", request).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(rateLimitService, never()).tryConsumeCpoLookup(anyString());
        verifyNoInteractions(stationSearchService);
    }

    @Test
    void textsucheBeiRegisterausfallIstEineLeereListe() {
        when(stationSearchService.search(anyString())).thenReturn(Optional.empty());

        ResponseEntity<List<StationMatch>> response = controller.searchStations("EnBW Lichtenau", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    /** Das Limit schuetzt das fremde Register: eine Antwort aus dem Cache kostet kein Kontingent. */
    @Test
    void gecachteStandorteVerbrauchenKeinKontingent() {
        when(nearbyCpoService.isStationsCached("u33dc0c", 1_000)).thenReturn(true);
        when(nearbyCpoService.findNearbyStations("u33dc0c", 1_000)).thenReturn(Optional.of(List.of()));

        ResponseEntity<?> response = controller.getNearbyStations(52.520008, 13.404954, 1_000, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(rateLimitService, never()).tryConsumeCpoLookup(anyString());
    }

    @Test
    void ungecachteStandorteVerbrauchenKontingent() {
        when(nearbyCpoService.isStationsCached("u33dc0c", null)).thenReturn(false);
        when(nearbyCpoService.findNearbyStations("u33dc0c")).thenReturn(Optional.of(List.of()));

        controller.getNearbyStations(52.520008, 13.404954, null, request);

        verify(rateLimitService).tryConsumeCpoLookup(anyString());
    }

    @Test
    void gecachteAnbieterVerbrauchenKeinKontingent() {
        when(nearbyCpoService.isCposCached("u33dc0c")).thenReturn(true);
        when(nearbyCpoService.findNearbyCpos("u33dc0c")).thenReturn(Optional.of(List.of("EnBW")));

        controller.getNearbyCpos(52.520008, 13.404954, request);

        verify(rateLimitService, never()).tryConsumeCpoLookup(anyString());
    }

    /** Ungueltige Koordinaten bleiben 400, auch ohne Kontingent-Abfrage davor. */
    @Test
    void ungueltigeKoordinatenSindWeiterBadRequest() {
        ResponseEntity<?> response = controller.getNearbyStations(91, 13.40, null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
