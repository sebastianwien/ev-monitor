package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteRepository;
import com.evmonitor.domain.ChargingSiteSource;
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
    private ChargingSiteService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ChargingSiteRepository.class);
        nearby = mock(NearbyCpoService.class);
        rateLimit = mock(RateLimitService.class);
        when(rateLimit.tryConsumeCpoLookup(any())).thenReturn(true);
        service = new ChargingSiteService(repository, nearby, rateLimit);
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
}
