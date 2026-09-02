package com.evmonitor.application.savings;

import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.RegionMedian;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.YearPrice;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository.YearTotals;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final ChargingPriceCache priceCache;
    private final HomeChargingProfileProvider profiles;

    /** Geohash-Laengen der Regionsstufe, von fein nach grob. 5 entspricht ~5 km, 3 ~156 km.
     *  Als Liste konfiguriert, damit eine weitere Stufe keine Codeaenderung braucht. */
    private final List<Integer> regionPrefixLengths;

    public HomeChargingSavingsService(ChargingSavingsQueryRepository repository,
                                      ChargingPriceCache priceCache,
                                      HomeChargingProfileProvider profiles,
                                      @Value("${savings.region-prefix-lengths:5,3}") List<Integer> regionPrefixLengths) {
        this.repository = repository;
        this.priceCache = priceCache;
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
                yearlySavings(userId, profile.country()));
    }

    /**
     * Ersparnis je Kalenderjahr, jedes mit dem oeffentlichen Preisniveau seines Jahres.
     *
     * Eine Hochrechnung der aktuellen Ersparnis ueber die gesamte Nutzungsdauer waere
     * doppelt falsch: sie unterstellt, es sei immer schon daheim geladen worden - auf
     * Prod liegen zwischen erstem Log und erster Heimladung bis zu 3,4 Jahre - und sie
     * unterstellt konstante Preise ueber die Energiekrise hinweg.
     */
    private List<YearlySaving> yearlySavings(UUID userId, String country) {
        Map<Integer, BigDecimal> priceByYear = priceCache
                .publicPriceByYear(userId, country, ChargingPriceResolver.MIN_OWN_PUBLIC_LOGS, COUNTRY_MIN_LOGS)
                .stream()
                .filter(p -> p.pricePerKwh() != null)
                .collect(Collectors.toMap(YearPrice::year, YearPrice::pricePerKwh, (a, b) -> a));

        List<HomeChargingYear> years = repository.homeYearTotals(userId).stream()
                .map((YearTotals y) -> new HomeChargingYear(
                        y.year(), y.kwh(), y.paidEur(), priceByYear.get(y.year())))
                .toList();

        return YearlySavingsCalculator.cumulate(years);
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
            RegionMedian region = priceCache.regionMedian(
                    anchor.substring(0, length), country, REGION_MIN_LOGS, REGION_MIN_CARS);
            if (region != null && region.median() != null) {
                return new PriceBasis(PriceSource.REGION, region.median(), region.sampleSize());
            }
        }
        return null;
    }

    private PriceBasis countryPrice(String country) {
        RegionMedian median = priceCache.countryMedian(country, COUNTRY_MIN_LOGS);
        return median != null && median.median() != null
                ? new PriceBasis(PriceSource.COUNTRY, median.median(), median.sampleSize())
                : null;
    }

    /** Nutzerbezogene Stammdaten der Rechnung. */
    public record HomeChargingProfile(String country, BigDecimal homeCardPricePerKwh, BigDecimal investmentEur) {}

    /** Laedt Land, Heimstrom-Ladekarte und Investition. Eigene Schnittstelle, damit der
     *  Service ohne Nutzer- und Kartenverwaltung testbar bleibt. */
    public interface HomeChargingProfileProvider {
        HomeChargingProfile forUser(UUID userId);
    }
}
