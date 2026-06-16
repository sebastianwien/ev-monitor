package com.evmonitor.application;

import com.evmonitor.domain.AppBundle;
import com.evmonitor.domain.AppBundleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression: die Caching-Annotationen auf {@link AppUpdateService} muessen gegen den
 * real konfigurierten (Caffeine-)CacheManager aufloesbar sein.
 *
 * <p>Der reine Unit-Test ({@code AppUpdateServiceTest}) instanziiert den Service per
 * {@code new} und umgeht damit den Caching-Proxy - er konnte deshalb nicht erkennen, dass
 * der Cache-Name {@code appUpdateCheck} in {@code spring.cache.cache-names} fehlte. Auf Prod
 * (Caffeine mit fixer Cache-Namensliste) warf Spring dann beim Publish
 * "Cannot find cache named 'appUpdateCheck'", was der globale Handler als 400 ausgab.
 *
 * <p>Hier laeuft der echte Bean durch den Spring-Context (Repository gemockt, keine DB),
 * damit der Caching-Aspekt tatsaechlich greift.
 */
@SpringBootTest
@ActiveProfiles("test")
class AppUpdateServiceCachingTest {

    @Autowired
    private AppUpdateService service;

    @MockitoBean
    private AppBundleRepository repository;

    @Test
    void publish_evictsCache_withoutMissingCacheError() {
        when(repository.findByVersion("1.0.99")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() -> service.publish("1.0.99", "sum"));
    }

    @Test
    void findUpdateFor_resolvesCache_withoutMissingCacheError() {
        when(repository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> service.findUpdateFor("1.0.0"));
    }
}
