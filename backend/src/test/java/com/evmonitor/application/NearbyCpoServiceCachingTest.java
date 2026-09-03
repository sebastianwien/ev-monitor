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
            return new ConcurrentMapCacheManager("nearbyCpos");
        }

        @Bean
        ChargingStationRegistryClient registry() {
            return mock(ChargingStationRegistryClient.class);
        }

        @Bean
        NearbyCpoService nearbyCpoService(ChargingStationRegistryClient registry) {
            return new NearbyCpoService(registry,
                    new CpoRegistryMatcher(List.of("Allego"), Map.of()), 250);
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
}
