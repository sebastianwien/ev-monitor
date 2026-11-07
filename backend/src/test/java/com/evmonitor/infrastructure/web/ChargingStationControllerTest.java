package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingStationMap;
import com.evmonitor.application.ChargingStationMapService;
import com.evmonitor.application.CpoPriceBenchmarkService;
import com.evmonitor.application.CpoTariffs;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Die Karte schickt nur Geohash-6-Zellen, nie eine Position. Geprüft werden Eingabegrenzen und
 * dass die Drossel nur nicht gecachte Zellen zählt, ohne die ganze Antwort abzulehnen.
 */
class ChargingStationControllerTest {

    private ChargingStationMapService mapService;
    private CpoPriceBenchmarkService benchmarkService;
    private RateLimitService rateLimitService;
    private ChargingStationController controller;
    private Authentication request;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mapService = mock(ChargingStationMapService.class);
        benchmarkService = mock(CpoPriceBenchmarkService.class);
        rateLimitService = mock(RateLimitService.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        request = mock(Authentication.class);
        when(request.getPrincipal()).thenReturn(UserPrincipal.create(user));
        when(mapService.stations(anyList(), any())).thenReturn(new ChargingStationMap(List.of(), List.of()));
        controller = new ChargingStationController(mapService, benchmarkService, rateLimitService);
    }

    @Test
    void reichtDieZellenWeiterOhneDoppelte() {
        var response = controller.stations("u1qcvx,u1qcvz, u1qcvx", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(mapService).stations(eq(List.of("u1qcvx", "u1qcvz")), any());
    }

    @Test
    void falscheZellenSind400() {
        assertThat(controller.stations("", request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.stations("u1qcv", request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.stations("u1qcvx,U1QCVZ", request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.stations("u1qcvx,'or1=1", request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(mapService);
    }

    /** Mehr als zwölf Zellen sind kein Kartenausschnitt mehr, sondern ein Abzug des Registers. */
    @Test
    void mehrAlsZwoelfZellenSind400() {
        String thirteen = String.join(",", List.of("u1qcv0", "u1qcv1", "u1qcv2", "u1qcv3", "u1qcv4", "u1qcv5",
                "u1qcv6", "u1qcv7", "u1qcv8", "u1qcv9", "u1qcvb", "u1qcvc", "u1qcvd"));

        assertThat(controller.stations(thirteen, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @SuppressWarnings("unchecked")
    @Test
    void drosselGiltJeNutzer() {
        when(rateLimitService.tryConsumeStationCells(anyString())).thenReturn(false);

        controller.stations("u1qcvx", request);

        ArgumentCaptor<Predicate<String>> mayAsk = ArgumentCaptor.forClass(Predicate.class);
        verify(mapService).stations(anyList(), mayAsk.capture());
        assertThat(mayAsk.getValue().test("u1qcvx")).isFalse();
        verify(rateLimitService).tryConsumeStationCells("user:" + userId);
    }

    @Test
    void tarifeAnDerSaeuleMitLeistung() {
        var tariffs = new CpoTariffs("EnBW", ChargingType.DC, List.of());
        when(benchmarkService.stationTariffs("EnBW", ChargingType.DC, 150)).thenReturn(Optional.of(tariffs));

        var response = controller.tariffs("EnBW", ChargingType.DC, 150);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(tariffs);
    }

    @Test
    void tarifeOhneTreffer204() {
        when(benchmarkService.stationTariffs(anyString(), any(), any())).thenReturn(Optional.empty());

        assertThat(controller.tariffs("Unbekannt", ChargingType.AC, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void tarifeMitUngueltigenWerten400() {
        assertThat(controller.tariffs(" ", ChargingType.DC, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.tariffs("x".repeat(101), ChargingType.DC, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.tariffs("EnBW", ChargingType.UNKNOWN, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.tariffs("EnBW", ChargingType.DC, 0).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.tariffs("EnBW", ChargingType.DC, 2000).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(benchmarkService);
    }
}
