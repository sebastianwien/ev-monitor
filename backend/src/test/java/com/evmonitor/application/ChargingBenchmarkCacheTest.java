package com.evmonitor.application;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository.PublicChargePrice;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Der Preisvergleich liest die Ladungen aller Nutzer. Das darf nicht je Anfrage passieren:
 * die Abfrage wächst mit dem ganzen Bestand. Geprüft mit echtem Spring-Proxy, ein
 * {@code @Cacheable} innerhalb derselben Bean wäre sonst unbemerkt wirkungslos.
 */
@SpringJUnitConfig(ChargingBenchmarkCacheTest.TestConfig.class)
class ChargingBenchmarkCacheTest {

    @Autowired ChargingBenchmarkCache cache;
    @Autowired ChargingBenchmarkQueryRepository repository;

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager(ChargingBenchmarkCache.CACHE);
        }

        @Bean
        ChargingBenchmarkQueryRepository repository() {
            return mock(ChargingBenchmarkQueryRepository.class);
        }

        @Bean
        ChargingBenchmarkCache chargingBenchmarkCache(ChargingBenchmarkQueryRepository repository) {
            return new ChargingBenchmarkCache(repository);
        }
    }

    @Test
    void readsAllChargesOnce_overTheLastTwelveMonths() {
        List<PublicChargePrice> rows = List.of(new PublicChargePrice(UUID.randomUUID(), "EnBW", ChargingType.DC, new BigDecimal("0.59"), null, null));
        when(repository.publicChargePrices(any())).thenReturn(rows);

        assertThat(cache.publicChargePrices()).isEqualTo(rows);
        assertThat(cache.publicChargePrices()).isEqualTo(rows);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository, times(1)).publicChargePrices(since.capture());
        assertThat(since.getValue()).isCloseTo(LocalDateTime.now().minusMonths(12), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
    }
}
