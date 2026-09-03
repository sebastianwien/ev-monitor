package com.evmonitor.infrastructure.config;

import com.evmonitor.application.CpoRegistryMatcher;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.infrastructure.persistence.ChargingNetworkAliasEntity;
import com.evmonitor.infrastructure.persistence.JpaChargingNetworkAliasRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Verdrahtet den Abgleich zwischen Ladesaeulenregister und unseren Ladenetzen.
 */
@Configuration
@Slf4j
public class CpoRegistryConfig {

    /**
     * Kanonische Namen und Firmierungs-Aliase werden einmal beim Start geladen. Beide
     * Tabellen aendern sich nur per Migration, ein Neustart gehoert dort ohnehin dazu.
     */
    @Bean
    public CpoRegistryMatcher cpoRegistryMatcher(ChargingProviderTariffRepository tariffRepository,
                                                 JpaChargingNetworkAliasRepository aliasRepository) {
        Map<String, String> aliases = aliasRepository.findAll().stream()
                .collect(Collectors.toMap(ChargingNetworkAliasEntity::getAlias,
                        ChargingNetworkAliasEntity::getNetworkName, (a, b) -> a));
        var knownCpos = tariffRepository.findAllKnownCpoNames();
        log.info("CPO-Registerabgleich: {} Ladenetze, {} Aliase", knownCpos.size(), aliases.size());
        return new CpoRegistryMatcher(knownCpos, aliases);
    }

    /**
     * Eigene Cache-Regel fuer die Umkreisvorschlaege: der Registerbestand aendert sich
     * monatlich, die globalen vier Stunden waeren hier reine Last beim fremden Dienst.
     * Der Platz reicht fuer 50.000 Geohash-Zellen und kostet nur die Namensliste je Zelle.
     */
    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> nearbyCposCacheCustomizer() {
        return cacheManager -> cacheManager.registerCustomCache("nearbyCpos",
                Caffeine.newBuilder()
                        .maximumSize(50_000)
                        .expireAfterWrite(Duration.ofDays(30))
                        .build());
    }
}
