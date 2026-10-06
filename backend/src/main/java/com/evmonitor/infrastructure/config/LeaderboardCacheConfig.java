package com.evmonitor.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Eigene Laufzeiten für Bestenliste und Ticker neben dem globalen 4-h-Default:
 * Rankings je Kategorie und Zeitfenster 15 Minuten (für alle Nutzer gleich),
 * "Heute" 15 Minuten (sonst stünde abends "heute" für gestern, Opt-out wirkt schnell),
 * persönliche Einträge je Nutzer eine Stunde.
 */
@Configuration
public class LeaderboardCacheConfig {

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> leaderboardCacheCustomizer() {
        return cacheManager -> {
            // 8 Kategorien mal wenige Zeitfenster (Monat bis heute, bis gestern, Vormonat)
            cacheManager.registerCustomCache("leaderboardRanking", Caffeine.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(Duration.ofMinutes(15))
                    .build());
            cacheManager.registerCustomCache("tickerToday", Caffeine.newBuilder()
                    .maximumSize(1)
                    .expireAfterWrite(Duration.ofMinutes(15))
                    .build());
            cacheManager.registerCustomCache("tickerPersonal", Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(Duration.ofHours(1))
                    .build());
        };
    }
}
