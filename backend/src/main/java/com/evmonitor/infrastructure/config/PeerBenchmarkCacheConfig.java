package com.evmonitor.infrastructure.config;

import com.evmonitor.application.EvLogStatisticsService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Peer-Seite des Dashboard-Vergleichs (Verbrauch und Preis fremder Autos derselben Spec): 24 h statt
 * des globalen 4-h-Defaults. Ein Schnitt über fremde Autos darf einen Tag alt sein; die eigene Seite
 * wird immer live gerechnet. Ein Eintrag je Nutzer, Spec und Zeitraum.
 */
@Configuration
public class PeerBenchmarkCacheConfig {

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> peerBenchmarkCacheCustomizer() {
        return cacheManager -> cacheManager.registerCustomCache(EvLogStatisticsService.PEER_SIDE_CACHE, Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build());
    }
}
