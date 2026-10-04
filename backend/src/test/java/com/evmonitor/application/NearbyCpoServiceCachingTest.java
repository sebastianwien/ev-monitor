package com.evmonitor.application;

import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Prueft die Cache-Regel mit echtem Spring-Proxy.
 *
 * <p>Ohne diesen Test faellt nicht auf, dass Spring ein {@link Optional} auspackt, bevor es
 * den {@code unless}-Ausdruck auswertet - der Ausdruck laueft dann gegen die Liste statt
 * gegen das Optional. Die reinen Mockito-Tests sehen davon nichts, weil dort kein Proxy laeuft.
 */
@SpringJUnitConfig(NearbyCpoServiceCachingTest.TestConfig.class)
class NearbyCpoServiceCachingTest {

    @Autowired
    private NearbyCpoService service;

    @Autowired
    private ChargingStationRegistryClient registry;

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager("nearbyCpos", "nearbyStations");
        }

        @Bean
        ChargingStationRegistryClient registry() {
            return mock(ChargingStationRegistryClient.class);
        }

        @Bean
        NearbyCpoService nearbyCpoService(ChargingStationRegistryClient registry, ConcurrentMapCacheManager cacheManager) {
            return new NearbyCpoService(registry,
                    new CpoRegistryMatcher(List.of("Allego"), Map.of()), 250, cacheManager);
        }
    }

    @Test
    void fragtDasRegisterProZelleNurEinmal() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.of(List.of(new Station("Allego GmbH", null))));

        assertThat(service.findNearbyCpos("u33dc0d")).contains(List.of("Allego"));
        assertThat(service.findNearbyCpos("u33dc0d")).contains(List.of("Allego"));

        verify(registry, times(1)).findStationsNearby(anyDouble(), anyDouble(), anyInt());
    }

    /** "Dort steht nichts" ist eine gueltige Antwort und wird gecacht. */
    @Test
    void leeresErgebnisWirdGecacht() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.of(List.of()));

        assertThat(service.findNearbyCpos("u33dc0e")).contains(List.of());
        assertThat(service.findNearbyCpos("u33dc0e")).contains(List.of());

        verify(registry, times(1)).findStationsNearby(anyDouble(), anyDouble(), anyInt());
    }

    /** Ein Ausfall darf sich nicht dreissig Tage lang im Cache halten. */
    @Test
    void ausfallWirdNichtGecacht() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.empty());

        assertThat(service.findNearbyCpos("u33dc0f")).isEmpty();
        assertThat(service.findNearbyCpos("u33dc0f")).isEmpty();

        verify(registry, times(2)).findStationsNearby(anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void cachtStandortvorschlaegeEbenfallsProZelle() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.of(List.of(new Station("Allego GmbH", null, 52.5204, 13.4046, 150.0, true, 2))));

        assertThat(service.findNearbyStations("u33dc0f")).isPresent();
        assertThat(service.findNearbyStations("u33dc0f")).isPresent();

        verify(registry, times(1)).findStationsNearby(anyDouble(), anyDouble(), anyInt());
    }

    /** Der weite Umkreis hat einen eigenen Schluessel - sonst bekaeme die enge Suche die weite Antwort. */
    @Test
    void weiterUmkreisWirdGetrenntVonDerEngenSucheGecacht() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.of(List.of(new Station("Allego GmbH", null, 52.5204, 13.4046, 150.0, true, 2))));

        service.findNearbyStations("u33dc0p");
        service.findNearbyStations("u33dc0p", 2_500);
        service.findNearbyStations("u33dc0p", 2_500);

        verify(registry, times(1)).findStationsNearby(anyDouble(), anyDouble(), eq(250));
        verify(registry, times(1)).findStationsNearby(anyDouble(), anyDouble(), eq(2_500));
    }

    /** Der Radius ist nach oben begrenzt - das Register liefert sonst Tausende Saeulen. */
    @Test
    void radiusIstNachObenBegrenzt() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of()));

        service.findNearbyStations("u33dc0n", 50_000);

        verify(registry).findStationsNearby(anyDouble(), anyDouble(), eq(NearbyCpoService.MAX_RADIUS_METERS));
    }

    @Test
    void ausfallWirdBeiStandortenNichtGecacht() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(List.of()));

        assertThat(service.findNearbyStations("u33dc0g")).isEmpty();
        assertThat(service.findNearbyStations("u33dc0g")).isPresent();

        verify(registry, times(2)).findStationsNearby(anyDouble(), anyDouble(), anyInt());
    }

    /** Grundlage der Drosselung: nur Antworten, die das Register nicht mehr fragen, sind kostenlos. */
    @Test
    void weissObEineAntwortSchonImCacheLiegt() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of()));

        assertThat(service.isStationsCached("u33dc0q", null)).isFalse();
        service.findNearbyStations("u33dc0q");
        service.findNearbyStations("u33dc0q", 1_000);
        service.findNearbyCpos("u33dc0q");

        assertThat(service.isStationsCached("u33dc0q", null)).isTrue();
        assertThat(service.isStationsCached("u33dc0q", 1_000)).isTrue();
        assertThat(service.isStationsCached("u33dc0q", 2_500)).isFalse();
        assertThat(service.isCposCached("u33dc0q")).isTrue();
        assertThat(service.isCposCached("u33dc0r")).isFalse();
    }

    /** Ein Ausfall landet nicht im Cache - der naechste Versuch fragt das Register und kostet wieder. */
    @Test
    void ausfallGiltNichtAlsGecacht() {
        reset(registry);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.empty());

        service.findNearbyStations("u33dc0s", 1_000);

        assertThat(service.isStationsCached("u33dc0s", 1_000)).isFalse();
    }
}
