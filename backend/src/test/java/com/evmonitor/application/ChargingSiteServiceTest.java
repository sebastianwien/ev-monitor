package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteRepository;
import com.evmonitor.domain.ChargingSiteSource;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.KnownCell;
import com.evmonitor.infrastructure.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Ein Ladestandort entsteht nur aus einer Saeule, die das Register in dieser Zelle wirklich
 * fuehrt. Der Client liefert nur Name und Zelle - Leistung und Ladepunkte kommen aus dem
 * (gecachten) Registerabruf, damit niemand erfundene Standorte in die geteilte Tabelle schreibt.
 */
class ChargingSiteServiceTest {

    private static final String CELL = "u33dc0c";
    private static final ChargingSiteRef IONITY = new ChargingSiteRef("IONITY", CELL);

    private ChargingSiteRepository repository;
    private NearbyCpoService nearby;
    private RateLimitService rateLimit;
    private EvLogRepository evLogs;
    private PlaceNameService placeNames;
    private ChargingSiteService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ChargingSiteRepository.class);
        nearby = mock(NearbyCpoService.class);
        rateLimit = mock(RateLimitService.class);
        evLogs = mock(EvLogRepository.class);
        when(rateLimit.tryConsumeCpoLookup(any())).thenReturn(true);
        placeNames = mock(PlaceNameService.class);
        when(evLogs.findKnownCells(any(), anyInt())).thenReturn(List.of());
        when(placeNames.nameFor(any())).thenReturn(Optional.empty());
        when(placeNames.cachedNameFor(any())).thenReturn(Optional.empty());
        service = new ChargingSiteService(repository, nearby, rateLimit, evLogs, placeNames);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static NearbyStation station(String name, boolean known, String geohash) {
        return new NearbyStation(name, known, 40, 350.0, true, 6, geohash);
    }

    @Test
    void nimmtEinenBekanntenStandortOhneRegisterabfrage() {
        ChargingSite existing = new ChargingSite(UUID.randomUUID(), "IONITY", "IONITY", CELL,
                null, new BigDecimal("350"), 6, ChargingSiteSource.REGISTER, ChargingSite.RegisterDetails.NONE, LocalDateTime.now());
        when(repository.findByGeohashAndName(CELL, "IONITY")).thenReturn(Optional.of(existing));

        assertThat(service.resolve(userId, IONITY)).contains(existing);
        verifyNoInteractions(nearby);
        verify(repository, never()).save(any());
    }

    @Test
    void legtEinenStandortAnWennDasRegisterIhnInDerZelleFuehrt() {
        when(repository.findByGeohashAndName(CELL, "IONITY")).thenReturn(Optional.empty());
        when(nearby.findNearbyStations(CELL)).thenReturn(Optional.of(List.of(station("IONITY", true, CELL))));

        ChargingSite site = service.resolve(userId, IONITY).orElseThrow();

        assertThat(site.name()).isEqualTo("IONITY");
        assertThat(site.cpoName()).isEqualTo("IONITY");
        assertThat(site.geohash()).isEqualTo(CELL);
        assertThat(site.maxDcKw()).isEqualByComparingTo("350");
        assertThat(site.maxAcKw()).isNull();
        assertThat(site.chargePoints()).isEqualTo(6);
        assertThat(site.fastCharging()).isTrue();
        assertThat(site.source()).isEqualTo(ChargingSiteSource.REGISTER);
        verify(repository).save(site);
    }

    /** Unbekannte Betreiber bleiben Standort, nur ohne Verweis in den Ladenetz-Katalog. */
    @Test
    void unbekannterBetreiberBekommtKeinenKatalogverweis() {
        when(repository.findByGeohashAndName(CELL, "Stadtwerke Musterstadt")).thenReturn(Optional.empty());
        when(nearby.findNearbyStations(CELL))
                .thenReturn(Optional.of(List.of(station("Stadtwerke Musterstadt", false, CELL))));

        ChargingSite site = service.resolve(userId, new ChargingSiteRef("Stadtwerke Musterstadt", CELL)).orElseThrow();

        assertThat(site.cpoName()).isNull();
    }

    @Test
    void vergleichtDenNamenOhneGrossKleinschreibung() {
        when(repository.findByGeohashAndName(CELL, "ionity")).thenReturn(Optional.empty());
        when(nearby.findNearbyStations(CELL)).thenReturn(Optional.of(List.of(station("IONITY", true, CELL))));

        assertThat(service.resolve(userId, new ChargingSiteRef("ionity", CELL))).isPresent();
    }

    @Test
    void lehntEinenStandortAbDenDasRegisterNichtKennt() {
        when(repository.findByGeohashAndName(CELL, "IONITY")).thenReturn(Optional.empty());
        when(nearby.findNearbyStations(CELL)).thenReturn(Optional.of(List.of(station("Allego", true, CELL))));

        assertThat(service.resolve(userId, IONITY)).isEmpty();
        verify(repository, never()).save(any());
    }

    /**
     * Die Umkreissuche fasst je Betreiber zusammen und traegt die Zelle der naechsten Saeule -
     * die kann knapp jenseits der Zellgrenze liegen. Der Standort bekommt deshalb die Zelle
     * aus dem Verweis, nicht die aus dem Registertreffer.
     */
    @Test
    void standortBekommtDieZelleAusDemVerweis() {
        when(repository.findByGeohashAndName(CELL, "IONITY")).thenReturn(Optional.empty());
        when(nearby.findNearbyStations(CELL)).thenReturn(Optional.of(List.of(station("IONITY", true, "u33dc0d"))));

        assertThat(service.resolve(userId, IONITY).orElseThrow().geohash()).isEqualTo(CELL);
    }

    /** Ein Registerabruf beim Speichern zaehlt auf dasselbe Kontingent wie die Umkreissuche. */
    @Test
    void ohneKontingentKeinRegisterabrufUndKeinStandort() {
        when(repository.findByGeohashAndName(CELL, "IONITY")).thenReturn(Optional.empty());
        when(rateLimit.tryConsumeCpoLookup("user:" + userId)).thenReturn(false);

        assertThat(service.resolve(userId, IONITY)).isEmpty();
        verifyNoInteractions(nearby);
    }

    /** Ein bereits bekannter Standort kostet kein Kontingent. */
    @Test
    void bekannterStandortVerbrauchtKeinKontingent() {
        when(repository.findByGeohashAndName(CELL, "IONITY")).thenReturn(Optional.of(new ChargingSite(
                UUID.randomUUID(), "IONITY", "IONITY", CELL, null, null, 2, ChargingSiteSource.REGISTER, ChargingSite.RegisterDetails.NONE, LocalDateTime.now())));

        service.resolve(userId, IONITY);
        verifyNoInteractions(rateLimit);
    }

    @Test
    void bleibtOhneStandortWennDasRegisterNichtAntwortet() {
        when(repository.findByGeohashAndName(CELL, "IONITY")).thenReturn(Optional.empty());
        when(nearby.findNearbyStations(CELL)).thenReturn(Optional.empty());

        assertThat(service.resolve(userId, IONITY)).isEmpty();
    }

    @Test
    void ohneReferenzKeinStandort() {
        assertThat(service.resolve(userId, null)).isEmpty();
        verifyNoInteractions(repository, nearby, rateLimit);
    }

    // ── Vorschlag: "du stehst an einem bekannten Ort" ─────────────────────────────

    /** Mittelpunkt der Zelle u33dc0c (Berlin, Alexanderplatz). */
    private static final double LAT = 52.5195, LON = 13.4054;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 27, 18, 0);

    private static ChargingSite site(String name, String geohash) {
        return new ChargingSite(UUID.randomUUID(), name, name, geohash, null, new BigDecimal("150"), 2,
                ChargingSiteSource.REGISTER, ChargingSite.RegisterDetails.NONE, LocalDateTime.now());
    }

    private static KnownCell publicCell(String geohash, long count, UUID siteId) {
        return new KnownCell(geohash, true, count, T0.minusDays(count), "EnBW", siteId, null);
    }

    private static KnownCell privateCell(String geohash, long count) {
        return new KnownCell(geohash, false, count, T0.minusDays(count), null, null, null);
    }

    private void cells(KnownCell... cells) {
        when(evLogs.findKnownCells(eq(userId), anyInt())).thenReturn(List.of(cells));
    }

    @Test
    void bekannteOrteOhnePositionNachHaeufigkeitOhneEntfernung() {
        cells(privateCell("u33dc0", 3), publicCell("u0zpr5p", 9, null));

        List<KnownPlace> places = service.knownPlaces(userId, null);

        assertThat(places).extracting(p -> p.cell().usageCount()).containsExactly(9L, 3L);
        assertThat(places).allSatisfy(p -> { assertThat(p.distanceMeters()).isNull(); assertThat(p.here()).isFalse(); });
    }

    @Test
    void reichertSaeulenInEinemZugriffAnUndFragtDenGeocoderNurFuerOrteOhneSaeule() {
        ChargingSite enbw = site("EnBW Alexanderplatz", CELL);
        cells(publicCell(CELL, 7, enbw.id()), privateCell("u33dc0", 3));
        when(repository.findAllById(List.of(enbw.id()))).thenReturn(List.of(enbw));
        when(placeNames.nameFor("u33dc0")).thenReturn(Optional.of("Mitte"));

        List<KnownPlace> places = service.knownPlaces(userId, null);

        assertThat(places.get(0).site()).isEqualTo(enbw);
        assertThat(places.get(0).placeName()).isNull();
        assertThat(places.get(1).placeName()).isEqualTo("Mitte");
        verify(placeNames, never()).nameFor(CELL);
    }

    @Test
    void mitPositionStehtDerOrtHierZuerstDanachNachHaeufigkeit() {
        // Alexanderplatz: private Zelle u33dc0 (hier), oeffentliche Zelle in Feuchtwangen (haeufiger, weit weg)
        cells(publicCell("u0zpr5p", 20, null), privateCell("u33dc0", 3), publicCell("u33dc0c", 1, null));

        List<KnownPlace> places = service.knownPlaces(userId, new Position(LAT, LON));

        assertThat(places).extracting(p -> p.cell().geohash()).containsExactly("u33dc0c", "u33dc0", "u0zpr5p");
        assertThat(places.get(0).here()).isTrue();
        assertThat(places.get(1).here()).isTrue();
        assertThat(places.get(2).here()).isFalse();
        assertThat(places.get(2).distanceMeters()).isGreaterThan(100_000);
    }

    @Test
    void hierGiltBeiOeffentlicherZelleBis300MeternBeiPrivaterBis800Metern() {
        // u33dc0c Mitte ist (LAT, LON); 500 m noerdlich liegt noch in der privaten 6er-Zelle, nicht mehr in der 7er
        cells(publicCell("u33dc0c", 2, null), privateCell("u33dc0", 2));

        List<KnownPlace> places = service.knownPlaces(userId, new Position(LAT + 0.0045, LON));

        assertThat(places).filteredOn(p -> p.cell().geohash().equals("u33dc0")).allMatch(KnownPlace::here);
        assertThat(places).filteredOn(p -> p.cell().geohash().equals("u33dc0c")).noneMatch(KnownPlace::here);
    }

    @Test
    void suggestLiefertDenOrtHierUndNichtsWennKeinerPasst() {
        cells(publicCell("u0zpr5p", 20, null), privateCell("u33dc0", 1));

        assertThat(service.suggest(userId, LAT, LON)).map(p -> p.cell().geohash()).contains("u33dc0");
        // Muenchen liegt in keiner bekannten Zelle
        assertThat(service.suggest(userId, 48.137, 11.575)).isEmpty();
    }

    /** Die Saeule ist die praezisere Aussage als die private Zelle am selben Ort - auch wenn sie seltener genutzt wurde. */
    @Test
    void saeuleSchlaegtDiePrivateZelleAmSelbenOrt() {
        ChargingSite enbw = site("EnBW Alexanderplatz", CELL);
        cells(privateCell("u33dc0", 12), publicCell(CELL, 2, enbw.id()));
        when(repository.findAllById(List.of(enbw.id()))).thenReturn(List.of(enbw));

        assertThat(service.suggest(userId, LAT, LON)).map(KnownPlace::site).contains(enbw);
    }

    @Test
    void jedeEinzelnePrivateZelleZaehltAlsBekannterOrt() {
        cells(privateCell("u33dc0", 1));

        assertThat(service.suggest(userId, LAT, LON)).isPresent();
    }

    /** Jedes frische Geocoding kostet rund eine Sekunde - mehr als fuenf je Anfrage wartet niemand. Gecachte Namen sind frei. */
    @Test
    void geocodiertHoechstensFuenfOrteJeAnfrageGecachteNamenZaehlenNicht() {
        KnownCell[] many = new KnownCell[8];
        for (int i = 0; i < 8; i++) many[i] = privateCell("u33dc" + i, 8 - i);
        cells(many);
        when(placeNames.cachedNameFor("u33dc0")).thenReturn(Optional.of("Mitte"));

        List<KnownPlace> places = service.knownPlaces(userId, null);

        assertThat(places.get(0).placeName()).isEqualTo("Mitte");
        verify(placeNames, never()).nameFor("u33dc0");
        verify(placeNames, times(ChargingSiteService.GEOCODE_PER_REQUEST)).nameFor(any());
    }
}
