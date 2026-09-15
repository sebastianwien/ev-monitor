package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingProviderTariffService;
import com.evmonitor.application.NearbyCpoService;
import com.evmonitor.application.NearbyStation;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Der Umkreis-Endpunkt reicht Koordinaten an einen fremden Dienst weiter.
 * Geprueft werden deshalb Eingabegrenzen, Drosselung und die Umwandlung in eine
 * Geohash-Zelle - die Rohkoordinaten duerfen den Server nicht verlassen.
 */
class ChargingProviderTariffControllerNearbyTest {

    private NearbyCpoService nearbyCpoService;
    private RateLimitService rateLimitService;
    private ChargingProviderTariffController controller;
    private Authentication request;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        nearbyCpoService = mock(NearbyCpoService.class);
        rateLimitService = mock(RateLimitService.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        request = mock(Authentication.class);
        when(request.getPrincipal()).thenReturn(UserPrincipal.create(user));
        when(rateLimitService.tryConsumeCpoLookup(anyString())).thenReturn(true);
        controller = new ChargingProviderTariffController(
                mock(ChargingProviderTariffService.class), nearbyCpoService, rateLimitService);
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
        verifyNoInteractions(nearbyCpoService);
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

        ResponseEntity<?> response = controller.getNearbyStations(52.520008, 13.404954, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(List.of(station));
        verify(nearbyCpoService).findNearbyStations("u33dc0c");
    }

    @Test
    void ausfallDesRegistersLiefertBeiStandortenEineLeereListe() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());

        assertThat(controller.getNearbyStations(52.52, 13.40, request).getBody()).isEqualTo(List.of());
    }

    @Test
    void standortabfrageIstGedrosseltUndGeprueft() {
        assertThat(controller.getNearbyStations(91.0, 13.4, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        when(rateLimitService.tryConsumeCpoLookup(anyString())).thenReturn(false);
        assertThat(controller.getNearbyStations(52.52, 13.40, request).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(nearbyCpoService);
    }
}
