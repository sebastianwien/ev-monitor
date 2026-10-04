package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingSiteService;
import com.evmonitor.application.KnownPlace;
import com.evmonitor.application.Position;
import com.evmonitor.domain.KnownCell;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Bekannte Orte sind Nutzerdaten: der Endpunkt fragt nur fuer den angemeldeten Nutzer, prueft die
 * Koordinaten und gibt die Position nur als Vergleichswert weiter - gespeichert wird nichts.
 */
class ChargingSiteControllerTest {

    private ChargingSiteService service;
    private ChargingSiteController controller;
    private Authentication request;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = mock(ChargingSiteService.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        request = mock(Authentication.class);
        when(request.getPrincipal()).thenReturn(UserPrincipal.create(user));
        controller = new ChargingSiteController(service);
    }

    private static KnownPlace place(String geohash, boolean isPublic, long count, boolean here) {
        var cell = new KnownCell(geohash, isPublic, count, LocalDateTime.of(2026, 9, 27, 18, 0), "Ionity", null, UUID.randomUUID());
        return new KnownPlace(cell, null, "Mitte", here);
    }

    @Test
    void ohnePositionDieOrteDesAngemeldetenNutzers() {
        when(service.knownPlaces(eq(userId), isNull())).thenReturn(List.of(place("u33dc0", false, 4, false)));

        var response = controller.known(null, null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var p = response.getBody().get(0);
        assertThat(p.geohash()).isEqualTo("u33dc0");
        assertThat(p.usageCount()).isEqualTo(4);
        assertThat(p.placeName()).isEqualTo("Mitte");
        assertThat(p.cpoName()).isEqualTo("Ionity");
        assertThat(p.lastProviderId()).isNotNull();
        assertThat(p.here()).isFalse();
    }

    @Test
    void mitPositionGehtSieNurAlsVergleichswertAnDenService() {
        when(service.knownPlaces(eq(userId), any(Position.class))).thenReturn(List.of(place("u33dc0c", true, 2, true)));

        var response = controller.known(52.52, 13.405, request);

        verify(service).knownPlaces(userId, new Position(52.52, 13.405));
        assertThat(response.getBody().get(0).here()).isTrue();
    }

    @Test
    void halbeOderUnmoeglicheKoordinatenSind400() {
        assertThat(controller.known(52.52, null, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.known(null, 13.4, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.known(91.0, 13.4, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.known(Double.NaN, 13.4, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void vorschlagIst204OhneTreffer() {
        when(service.suggest(userId, 52.52, 13.4)).thenReturn(Optional.empty());
        assertThat(controller.suggestion(52.52, 13.4, request).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
