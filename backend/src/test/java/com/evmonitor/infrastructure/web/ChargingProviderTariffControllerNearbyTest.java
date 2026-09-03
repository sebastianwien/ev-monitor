package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingProviderTariffService;
import com.evmonitor.application.NearbyCpoService;
import com.evmonitor.infrastructure.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

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
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        nearbyCpoService = mock(NearbyCpoService.class);
        rateLimitService = mock(RateLimitService.class);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
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

    @Test
    void drosselungZaehltProAufrufer() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");
        when(nearbyCpoService.findNearbyCpos(anyString())).thenReturn(Optional.of(List.of()));

        controller.getNearbyCpos(52.52, 13.40, request);

        verify(rateLimitService).tryConsumeCpoLookup("203.0.113.7");
    }
}
