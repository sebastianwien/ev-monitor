package com.evmonitor.infrastructure.route;

import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.route.RouteSketch;
import com.evmonitor.domain.route.RouteSketchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RouteSketchServiceTest {

    private OpenRouteServiceClient client;
    private RouteSketchRepository sketchRepository;
    private EvTripRepository tripRepository;
    private RouteSketchService service;

    private final UUID tripId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        client = mock(OpenRouteServiceClient.class);
        sketchRepository = mock(RouteSketchRepository.class);
        tripRepository = mock(EvTripRepository.class);
        service = new RouteSketchService(client, sketchRepository, tripRepository);
        when(sketchRepository.findByStartGeohashAndEndGeohash(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void berechnetUndPersistiertBeimErstenMal() {
        when(client.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of("polyline"));

        service.sketchTrip(tripId, "u336xp", "u33dbc");

        verify(tripRepository).updateRoutePolyline(tripId, "polyline");
        verify(sketchRepository).save(any(RouteSketch.class));
    }

    @Test
    void nutztDenCacheStattDenRouterErneutZuFragen() {
        when(sketchRepository.findByStartGeohashAndEndGeohash("u336xp", "u33dbc"))
                .thenReturn(Optional.of(new RouteSketch("u336xp", "u33dbc", "gecacht", LocalDateTime.now())));

        service.sketchTrip(tripId, "u336xp", "u33dbc");

        verify(tripRepository).updateRoutePolyline(tripId, "gecacht");
        verifyNoInteractions(client);
        verify(sketchRepository, never()).save(any());
    }

    @Test
    void ohneBeideGeohashesPassiertNichts() {
        service.sketchTrip(tripId, "u336xp", null);

        verifyNoInteractions(client);
        verify(tripRepository, never()).updateRoutePolyline(any(), any());
    }

    @Test
    void rundfahrtBrauchtKeineRoute() {
        service.sketchTrip(tripId, "u336xp", "u336xp");

        verifyNoInteractions(client);
        verify(tripRepository, never()).updateRoutePolyline(any(), any());
    }

    @Test
    void ohneAntwortDesRoutersBleibtDieFahrtOhneLinie() {
        when(client.route(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(Optional.empty());

        service.sketchTrip(tripId, "u336xp", "u33dbc");

        verify(tripRepository, never()).updateRoutePolyline(any(), any());
        verify(sketchRepository, never()).save(any());
    }
}
