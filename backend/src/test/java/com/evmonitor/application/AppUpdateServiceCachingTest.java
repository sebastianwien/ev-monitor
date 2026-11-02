package com.evmonitor.application;

import com.evmonitor.domain.AppBundle;
import com.evmonitor.testutil.AbstractServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThatNoException;

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
class AppUpdateServiceCachingTest extends AbstractServiceTest {

    @Autowired
    private AppUpdateService service;

    @Test
    void publish_evictsCache_withoutMissingCacheError() {
        assertThatNoException().isThrownBy(() -> service.publish("1.0.99", "sum"));
    }

    @Test
    void findUpdateFor_resolvesCache_withoutMissingCacheError() {
        assertThatNoException().isThrownBy(() -> service.findUpdateFor("1.0.0"));
    }
}
