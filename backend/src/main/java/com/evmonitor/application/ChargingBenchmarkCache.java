package com.evmonitor.application;

import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository.PublicChargePrice;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bezahlte oeffentliche Ladungen aller Nutzer der letzten 12 Monate, eine Stunde gecacht
 * (ChargingBenchmarkCacheConfig). Die Abfrage waechst mit dem ganzen Bestand, nicht mit den
 * Daten des Fragenden; je Anfrage gelesen liesse sie sich per Schleife zur Last machen. Der
 * Fragende wird erst danach im Speicher ausgeschlossen, das Ergebnis ist fuer alle gleich.
 *
 * Eigene Bean, weil {@code @Cacheable} innerhalb derselben Klasse nicht greift. Eine Stunde,
 * damit eine geloeschte Ladung nicht lange im Vergleich anderer nachwirkt.
 */
@Component
public class ChargingBenchmarkCache {

    public static final String CACHE = "chargingBenchmarkSamples";
    static final int WINDOW_MONTHS = 12;

    private final ChargingBenchmarkQueryRepository repository;

    public ChargingBenchmarkCache(ChargingBenchmarkQueryRepository repository) {
        this.repository = repository;
    }

    @Cacheable(CACHE)
    public List<PublicChargePrice> publicChargePrices() {
        return repository.publicChargePrices(LocalDateTime.now().minusMonths(WINDOW_MONTHS));
    }
}
