package com.evmonitor.application.savings;

import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.RegionMedian;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Heimlade-Ersparnis: was das Laden daheim gegenueber oeffentlichem Laden spart.
 *
 * Die Aussage ist kontrafaktisch ("haettest du dieselben kWh oeffentlich geladen"), nicht
 * gemessen. Deshalb traegt das Ergebnis immer mit, auf welcher Stufe die Preise ermittelt
 * wurden - die Kachel benennt sie, damit der Nutzer die Zahl einordnen kann.
 */
@Service
public class HomeChargingSavingsService {

    /** Mindestmass je Geohash-Zelle. Darunter waere der Median Zufall und die Zelle zu klein
     *  fuer Anonymitaet - ein einzelner Nachbar soll nicht herauslesbar sein. */
    private static final int REGION_MIN_LOGS = 10;
    private static final int REGION_MIN_CARS = 3;
    private static final int COUNTRY_MIN_LOGS = 20;

    private final ChargingSavingsQueryRepository repository;
    private final HomeChargingProfileProvider profiles;

    /** Geohash-Laengen der Regionsstufe, von fein nach grob. 5 entspricht ~5 km, 3 ~156 km.
     *  Als Liste konfiguriert, damit eine weitere Stufe keine Codeaenderung braucht. */
    private final List<Integer> regionPrefixLengths;

    public HomeChargingSavingsService(ChargingSavingsQueryRepository repository,
                                      HomeChargingProfileProvider profiles,
                                      @Value("${savings.region-prefix-lengths:5,3}") List<Integer> regionPrefixLengths) {
        this.repository = repository;
        this.profiles = profiles;
        this.regionPrefixLengths = regionPrefixLengths;
    }

    /**
     * @return {@code null}, wenn einer der beiden Preise unbekannt bleibt. Die Kachel zeigt
     *         dann ihren Leerzustand und bittet um den Heimstrompreis, statt zu schaetzen.
     */
    public ChargingSavings calculate(UUID userId) {
        HomeChargingProfile profile = profiles.forUser(userId);

        PriceBasis homePrice = ChargingPriceResolver.resolveHomePrice(
                repository.ownHomePrices(userId), profile.homeCardPricePerKwh());

        PriceBasis publicPrice = ChargingPriceResolver.resolvePublicPrice(
                repository.ownPublicPrices(userId),
                () -> regionPrice(userId, profile.country()),
                () -> countryPrice(profile.country()));

        return ChargingSavingsCalculator.calculate(
                repository.homeKwhLast12Months(userId),
                homePrice, publicPrice,
                profile.investmentEur(),
                repository.usageYears(userId));
    }

    /**
     * Umgebungspreis, von der feinsten Stufe abwaerts. Auf Prod traegt die feine Stufe
     * heute kaum - bei Geohash-5 erfuellt genau eine Zelle das Mindestmass. Die Kette
     * faellt dann durch, statt eine Zahl aus drei Ladungen zu behaupten, und greift von
     * selbst, sobald die Datendichte reicht.
     */
    private PriceBasis regionPrice(UUID userId, String country) {
        String anchor = repository.homeGeohash(userId);
        if (anchor == null) return null;

        for (int length : regionPrefixLengths) {
            if (anchor.length() < length) continue;
            RegionMedian region = cachedRegionMedian(anchor.substring(0, length), country);
            if (region != null && region.median() != null) {
                return new PriceBasis(PriceSource.REGION, region.median(), region.sampleSize());
            }
        }
        return null;
    }

    private PriceBasis countryPrice(String country) {
        RegionMedian median = cachedCountryMedian(country);
        return median != null && median.median() != null
                ? new PriceBasis(PriceSource.COUNTRY, median.median(), median.sampleSize())
                : null;
    }

    /** Gecacht statt materialisiert: die Abfrage laeuft in rund 20 ms ueber den gesamten
     *  Bestand. Eine Aggregat-Tabelle samt Scheduler waere dafuer zu viel Apparat. */
    @Cacheable(value = "chargingRegionMedian", key = "#prefix + '|' + #country")
    public RegionMedian cachedRegionMedian(String prefix, String country) {
        return repository.regionMedian(prefix, country, REGION_MIN_LOGS, REGION_MIN_CARS);
    }

    @Cacheable(value = "chargingCountryMedian", key = "#country")
    public RegionMedian cachedCountryMedian(String country) {
        return repository.countryMedian(country, COUNTRY_MIN_LOGS);
    }

    /** Nutzerbezogene Stammdaten der Rechnung. */
    public record HomeChargingProfile(String country, BigDecimal homeCardPricePerKwh, BigDecimal investmentEur) {}

    /** Laedt Land, Heimstrom-Ladekarte und Investition. Eigene Schnittstelle, damit der
     *  Service ohne Nutzer- und Kartenverwaltung testbar bleibt. */
    public interface HomeChargingProfileProvider {
        HomeChargingProfile forUser(UUID userId);
    }
}
