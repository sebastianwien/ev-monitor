package com.evmonitor.application.savings;

import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.RegionMedian;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.YearPrice;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Gecachte Vergleichspreise.
 *
 * Eigene Bean, weil Spring {@code @Cacheable} ueber einen Proxy aufloest: ein Aufruf
 * innerhalb derselben Bean laeuft daran vorbei und der Cache greift nie. Genau das ist
 * hier passiert, solange die Methoden im Service lagen - die Annotation war ein No-op.
 *
 * Gecacht statt materialisiert: die Abfragen laufen in rund 20 ms ueber den gesamten
 * Bestand. Eine Aggregat-Tabelle samt Scheduler waere dafuer zu viel Apparat.
 */
@Component
public class ChargingPriceCache {

    private final ChargingSavingsQueryRepository repository;

    public ChargingPriceCache(ChargingSavingsQueryRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = "chargingRegionMedian", key = "#prefix + '|' + #country")
    public RegionMedian regionMedian(String prefix, String country, int minLogs, int minCars) {
        return repository.regionMedian(prefix, country, minLogs, minCars);
    }

    @Cacheable(value = "chargingCountryMedian", key = "#country")
    public RegionMedian countryMedian(String country, int minLogs) {
        return repository.countryMedian(country, minLogs);
    }

    /**
     * Jahresreihe der oeffentlichen Preise. Der Nutzer geht in den Schluessel ein, weil
     * die Abfrage zuerst seine eigenen Ladungen heranzieht - ohne ihn bekaemen alle
     * dieselben Werte.
     */
    @Cacheable(value = "chargingPublicPriceByYear", key = "#userId + '|' + #country")
    public List<YearPrice> publicPriceByYear(UUID userId, String country, int minOwnLogs, int minCountryLogs) {
        return repository.publicPriceByYear(userId, country, minOwnLogs, minCountryLogs);
    }
}
