package com.evmonitor.application;

import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

/**
 * Cache-Regel der Kartenzellen mit echtem Spring-Proxy: eine Antwort bleibt, ein Ausfall nicht.
 */
@SpringJUnitConfig(ChargingStationCellServiceCachingTest.TestConfig.class)
class ChargingStationCellServiceCachingTest {

    @Autowired
    private ChargingStationCellService service;

    @Autowired
    private ChargingStationRegistryClient registry;

    @Autowired
    private ConcurrentMapCacheManager cacheManager;

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager(ChargingStationCellService.CACHE);
        }

        @Bean
        ChargingStationRegistryClient registry() {
            return mock(ChargingStationRegistryClient.class);
        }

        @Bean
        ChargingStationCellService chargingStationCellService(ChargingStationRegistryClient registry,
                                                              ConcurrentMapCacheManager cacheManager) {
            return new ChargingStationCellService(registry, new CpoRegistryMatcher(List.of("EnBW"), Map.of()), cacheManager);
        }
    }

    @BeforeEach
    void reset() {
        clearInvocations(registry);
        cacheManager.getCache(ChargingStationCellService.CACHE).clear();
    }

    @Test
    void fragtDasRegisterJeZelleNurEinmal() {
        when(registry.findStationsInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(List.of()));

        assertThat(service.isCached("u1qcvx")).isFalse();
        service.stationsInCell("u1qcvx");
        service.stationsInCell("u1qcvx");

        verify(registry, times(1)).findStationsInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(service.isCached("u1qcvx")).isTrue();
    }

    @Test
    void ausfallWirdNichtGecacht() {
        when(registry.findStationsInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        service.stationsInCell("u1qcvy");
        service.stationsInCell("u1qcvy");

        verify(registry, times(2)).findStationsInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(service.isCached("u1qcvy")).isFalse();
    }

    /** Ungültige Zellen gehen am Cache vorbei, statt am Schlüssel null zu scheitern. */
    @Test
    void ungueltigeZelleGehtAmCacheVorbei() {
        assertThat(service.stationsInCell(null)).isEmpty();
        assertThat(service.stationsInCell("U1QCVX")).isEmpty();
        assertThat(service.isCached(null)).isFalse();
        verifyNoInteractions(registry);
    }
}
