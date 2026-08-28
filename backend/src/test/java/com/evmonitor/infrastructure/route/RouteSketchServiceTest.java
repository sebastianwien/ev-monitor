package com.evmonitor.infrastructure.route;

import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.route.RouteSketch;
import com.evmonitor.domain.route.RouteSketchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
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
                .thenReturn(Optional.of(new OpenRouteServiceClient.Route("polyline", 12_000)));

        service.sketchTrip(tripId, "u336xp", "u33dbc");

        verify(tripRepository).updateRoutePolyline(tripId, "polyline", "SKETCH");
        verify(sketchRepository).save(any(RouteSketch.class));
    }

    @Test
    void nutztDenCacheStattDenRouterErneutZuFragen() {
        when(sketchRepository.findByStartGeohashAndEndGeohash("u336xp", "u33dbc"))
                .thenReturn(Optional.of(new RouteSketch("u336xp", "u33dbc", "gecacht", LocalDateTime.now())));

        service.sketchTrip(tripId, "u336xp", "u33dbc");

        verify(tripRepository).updateRoutePolyline(tripId, "gecacht", "SKETCH");
        verifyNoInteractions(client);
        verify(sketchRepository, never()).save(any());
    }

    @Test
    void ohneBeideGeohashesPassiertNichts() {
        service.sketchTrip(tripId, "u336xp", null);

        verifyNoInteractions(client);
        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
    }

    @Test
    void rundfahrtBrauchtKeineRoute() {
        service.sketchTrip(tripId, "u336xp", "u336xp");

        verifyNoInteractions(client);
        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
    }

    @Test
    void ohneAntwortDesRoutersBleibtDieFahrtOhneLinie() {
        when(client.route(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(Optional.empty());

        service.sketchTrip(tripId, "u336xp", "u33dbc");

        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
        verify(sketchRepository, never()).save(any());
    }

    // ── Map Matching: die Spur auf das Strassennetz legen ─────────────────────

    /** Die ersten beiden Punkte des Referenzbeispiels des Polyline-Formats: 38.5/-120.2, 40.7/-120.95. */
    private static final String TRACE = "_p~iF~ps|U_ulLnnqC";

    /** Gemessene Fahrleistung laut Kilometerzaehler - der Massstab fuer die gerechnete Linie. */
    private static final BigDecimal MEASURED_KM = new BigDecimal("12.0");

    @Test
    void legtDieSpurUeberIhreStuetzpunkteAufDieStrasse() {
        when(client.route(anyList())).thenReturn(Optional.of(new OpenRouteServiceClient.Route("gematcht", 12_000)));

        service.matchTrace(tripId, TRACE, MEASURED_KM);

        verify(tripRepository).updateRoutePolyline(tripId, "gematcht", "MATCHED");
    }

    @Test
    void gibtDemRouterDieStuetzpunkteInIhrerReihenfolge() {
        when(client.route(anyList())).thenReturn(Optional.of(new OpenRouteServiceClient.Route("gematcht", 12_000)));

        service.matchTrace(tripId, TRACE, MEASURED_KM);

        ArgumentCaptor<List<double[]>> waypoints = ArgumentCaptor.forClass(List.class);
        verify(client).route(waypoints.capture());
        assertThat(waypoints.getValue()).hasSize(2);
        assertThat(waypoints.getValue().get(0)[0]).isEqualTo(38.5, offset(1e-5));
        assertThat(waypoints.getValue().get(1)[0]).isEqualTo(40.7, offset(1e-5));
    }

    @Test
    void duenntLangeSpurenAufDasWegpunkteLimitAus() {
        when(client.route(anyList())).thenReturn(Optional.of(new OpenRouteServiceClient.Route("gematcht", 12_000)));
        StringBuilder longTrace = new StringBuilder("_p~iF~ps|U");
        for (int i = 0; i < 200; i++) longTrace.append("_ulLnnqC");

        service.matchTrace(tripId, longTrace.toString(), MEASURED_KM);

        ArgumentCaptor<List<double[]>> waypoints = ArgumentCaptor.forClass(List.class);
        verify(client).route(waypoints.capture());
        assertThat(waypoints.getValue()).hasSizeLessThanOrEqualTo(OpenRouteServiceClient.MAX_WAYPOINTS);
    }

    @Test
    void eineSpurAusEinemPunktIstKeineStrecke() {
        service.matchTrace(tripId, "_p~iF~ps|U", MEASURED_KM);

        verifyNoInteractions(client);
        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
    }

    @Test
    void ohneSpurPassiertNichts() {
        service.matchTrace(tripId, null, MEASURED_KM);
        service.matchTrace(tripId, "", MEASURED_KM);

        verifyNoInteractions(client);
        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
    }

    @Test
    void scheitertDerRouterBleibtDieRoheSpurStehen() {
        when(client.route(anyList())).thenReturn(Optional.empty());

        service.matchTrace(tripId, TRACE, MEASURED_KM);

        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
        verify(sketchRepository, never()).save(any());
    }

    @Test
    void gematchteRoutenLandenNichtImGeohashCache() {
        // Der Cache liegt auf dem Start-Ziel-Paar. Zwei Fahrten derselben Relation haben
        // verschiedene Stuetzpunkte - ein Treffer waere die Spur der jeweils anderen Fahrt.
        when(client.route(anyList())).thenReturn(Optional.of(new OpenRouteServiceClient.Route("gematcht", 12_000)));

        service.matchTrace(tripId, TRACE, MEASURED_KM);

        verifyNoInteractions(sketchRepository);
    }

    // ── Plausibilitaet: eine Route, die nicht zur Fahrt passt, ist keine ──────

    /**
     * Der Router faehrt jeden Stuetzpunkt exakt an. Liegt einer neben der befahrenen Strasse,
     * baut er dort eine Schleife - das Ergebnis ist dann laenger als die Fahrt je war. Solche
     * Linien duerfen nicht als gemessene Strecke durchgehen.
     */
    @Test
    void verwirftEineRouteDieVielLaengerIstAlsDieGefahreneStrecke() {
        when(client.route(anyList()))
                .thenReturn(Optional.of(new OpenRouteServiceClient.Route("umweg", 30_000)));

        service.matchTrace(tripId, TRACE, MEASURED_KM);

        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
    }

    @Test
    void verwirftEineRouteDieVielKuerzerIstAlsDieGefahreneStrecke() {
        when(client.route(anyList()))
                .thenReturn(Optional.of(new OpenRouteServiceClient.Route("abkuerzung", 3_000)));

        service.matchTrace(tripId, TRACE, MEASURED_KM);

        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
    }

    @Test
    void kleineAbweichungenSindNormalUndBleibenErlaubt() {
        // Die Spur schneidet Kurven ab, die Strasse macht sie mit - ein Aufschlag gehoert dazu.
        when(client.route(anyList()))
                .thenReturn(Optional.of(new OpenRouteServiceClient.Route("passt", 13_500)));

        service.matchTrace(tripId, TRACE, MEASURED_KM);

        verify(tripRepository).updateRoutePolyline(tripId, "passt", "MATCHED");
    }

    @Test
    void ohneGemesseneDistanzGibtEsKeinenMassstabUndDamitKeineLinie() {
        when(client.route(anyList()))
                .thenReturn(Optional.of(new OpenRouteServiceClient.Route("ungeprueft", 12_000)));

        service.matchTrace(tripId, TRACE, null);

        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
    }

    @Test
    void ohneLaengenangabeDesRoutersBleibtDieLinieUngeprueft() {
        // Fehlt die summary, kann nichts verglichen werden - dann lieber die rohe Spur behalten.
        when(client.route(anyList()))
                .thenReturn(Optional.of(new OpenRouteServiceClient.Route("ohne-laenge", 0)));

        service.matchTrace(tripId, TRACE, MEASURED_KM);

        verify(tripRepository, never()).updateRoutePolyline(any(), any(), any());
    }
}
