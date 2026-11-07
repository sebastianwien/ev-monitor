package com.evmonitor.application;

import com.evmonitor.application.CpoPriceBenchmark.CardOffer;
import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.application.tariff.TariffCatalog;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository.PublicChargePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Preisvergleich je Betreiber und Ladeart fuer den Story-Moment "zu teuer geladen".
 *
 * <p>Jeder Fahrer zaehlt einmal, mit dem Median seiner eigenen Ladungen - ein Vielfahrer mit
 * dreissig Ladungen verschiebt den Wert sonst allein. Der fragende Fahrer zaehlt nie: der
 * Vergleich heisst "andere Fahrer", und mit ihm liesse sich bei wenigen Fahrern ein fremder
 * Einzelwert zurueckrechnen.
 *
 * <p>Karten gelten nur dort, wo sie belegt sind: von Fahrern an diesem Betreiber bezahlt, oder
 * als gepflegter Tarif im eigenen Netz, ueber das Tier-Mapping oder mit ausdruecklichem
 * Betreiber. Ein Kartenpreis "ueberall" waere beim Roaming falsch (IONITY Power kostet bei Aral
 * nicht 39 ct).
 */
@Service
@RequiredArgsConstructor
public class CpoPriceBenchmarkService {

    /** Andere Fahrer, ab denen ein Betreiberwert erscheint (Datenschutz, keine Einzelwerte). */
    static final int MIN_DRIVERS = 5;
    /** Andere Fahrer, ab denen eine von Fahrern genutzte Karte erscheint. */
    static final int MIN_CARD_DRIVERS = 3;
    /** Ausserhalb ist ein Preis ein Tippfehler (2,06 statt 0,206) oder fast gratis, kein Tarif. */
    static final BigDecimal MIN_PLAUSIBLE = new BigDecimal("0.15");
    static final BigDecimal MAX_PLAUSIBLE = new BigDecimal("1.20");
    static final int MAX_CARDS = 5;

    private final ChargingBenchmarkCache benchmarkCache;
    private final TariffCatalog catalog;
    private final CpoNameNormalizer cpoNameNormalizer;

    public Optional<CpoPriceBenchmark> benchmark(String cpo, ChargingType type, UUID requesterId) {
        String operator = cpoNameNormalizer.canonicalOperator(cpo);
        if (operator == null || type == null || type == ChargingType.UNKNOWN) return Optional.empty();

        List<PublicChargePrice> others = benchmarkCache.publicChargePrices().stream()
                .filter(r -> r.chargingType() == type)
                .filter(r -> !r.driverId().equals(requesterId))
                .filter(r -> plausible(r.pricePerKwh()))
                .filter(r -> operator.equals(cpoNameNormalizer.canonicalOperator(r.cpoName())))
                .toList();

        List<BigDecimal> perDriver = driverMedians(others);
        if (perDriver.size() < MIN_DRIVERS) return Optional.empty();

        return Optional.of(new CpoPriceBenchmark(operator, type, perDriver.size(),
                round(percentile(perDriver, 0.5)),
                round(percentile(perDriver, 0.25)),
                round(percentile(perDriver, 0.75)),
                cards(operator, type, others)));
    }

    /**
     * Heute gültige, nicht dynamische Katalog-Tarife an diesem Betreiber, günstigste zuerst.
     * Unabhängig von Fahrerdaten: der Vergleich "was hätte ich mit einer anderen Karte gezahlt"
     * braucht belegte Preise, keine Mediane aus Aktionswochen.
     */
    public Optional<CpoTariffs> tariffs(String cpo, ChargingType type) {
        return tariffs(cpo, type, null, catalog.on(LocalDate.now()));
    }

    /**
     * Wie {@link #tariffs(String, ChargingType)}, für eine Säule in Deutschland mit bekannter Leistung:
     * die kW-Stufe gilt, Anbieter aus anderen Ländern fallen weg (siehe {@link TariffCatalog#forGermanyOn}).
     *
     * @param powerKw höchste Leistung der Säule für diese Ladeart; null, wenn unbekannt
     */
    public Optional<CpoTariffs> stationTariffs(String cpo, ChargingType type, Integer powerKw) {
        return tariffs(cpo, type, powerKw, catalog.forGermanyOn(LocalDate.now()));
    }

    private Optional<CpoTariffs> tariffs(String cpo, ChargingType type, Integer powerKw, TariffCatalog.Snapshot snapshot) {
        String operator = cpoNameNormalizer.canonicalOperator(cpo);
        if (operator == null || type == null || type == ChargingType.UNKNOWN) return Optional.empty();
        List<CpoTariffs.Tariff> list = snapshot.at(operator, type, powerKw).stream()
                .map(t -> toTariff(t, operator))
                .toList();
        return list.isEmpty() ? Optional.empty() : Optional.of(new CpoTariffs(operator, type, list));
    }

    /** Eine Katalogzeile für die Anzeige; {@code direct}, wenn der Kartenanbieter der Betreiber selbst ist. */
    public static CpoTariffs.Tariff toTariff(ChargingProviderTariff t, String operator) {
        return new CpoTariffs.Tariff(t.getEmpName(), t.getTariffVariant(), round(t.getPricePerKwh()), t.getMonthlyFeeEur(),
                verifiedOn(t), operator.equals(t.getEmpName()),
                t.getSessionFeeEur(), t.getBlockingFeePerMin(), t.getBlockingFeeAfterMin(), t.getBlockingFeeCapEur());
    }

    /** Gepflegt ersetzt die Fahrerkarte desselben Anbieters - der Tarif kennt Variante und Grundgebuehr. */
    private List<CardOffer> cards(String operator, ChargingType type, List<PublicChargePrice> others) {
        List<CardOffer> curated = curatedOffers(operator, type);
        Set<String> curatedEmps = curated.stream().map(CardOffer::name).collect(Collectors.toSet());
        Stream<CardOffer> byDrivers = driverCards(others).stream()
                .filter(c -> !curatedEmps.contains(c.name()));
        return Stream.concat(curated.stream(), byDrivers)
                .sorted(Comparator.comparing(CardOffer::pricePerKwh)
                        .thenComparing(CardOffer::source, Comparator.reverseOrder())
                        .thenComparing(CardOffer::name))
                .limit(MAX_CARDS)
                .toList();
    }

    /**
     * Nur Karten, die einem Anbieter im Katalog zugeordnet sind, unter dessen Namen. Freitext
     * ("MeineKarte") bleibt draussen: sonst setzen drei Konten eine beliebige Karte mit
     * beliebigem Preis an die Spitze, sichtbar fuer alle.
     */
    private List<CardOffer> driverCards(List<PublicChargePrice> others) {
        Map<String, List<PublicChargePrice>> byEmp = others.stream()
                .filter(r -> r.cardEmp() != null)
                .collect(Collectors.groupingBy(PublicChargePrice::cardEmp));
        return byEmp.entrySet().stream()
                .map(e -> {
                    List<BigDecimal> perDriver = driverMedians(e.getValue());
                    return perDriver.size() < MIN_CARD_DRIVERS ? null
                            : new CardOffer(e.getKey(), null, round(percentile(perDriver, 0.5)),
                                    null, CardOffer.Source.DRIVERS, perDriver.size(), null);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** Je Anbieter und Variante die genaueste Zeile für diesen Betreiber (siehe {@link TariffCatalog}). */
    private List<CardOffer> curatedOffers(String operator, ChargingType type) {
        return catalog.on(LocalDate.now()).at(operator, type, null).stream()
                .map(t -> new CardOffer(t.getEmpName(), t.getTariffVariant(), round(t.getPricePerKwh()), t.getMonthlyFeeEur(),
                        CardOffer.Source.CURATED, null,
                        verifiedOn(t)))
                .toList();
    }

    private static LocalDate verifiedOn(ChargingProviderTariff t) {
        return t.getLastVerifiedAt() == null ? null : t.getLastVerifiedAt().toLocalDate();
    }

    private static List<BigDecimal> driverMedians(List<PublicChargePrice> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(PublicChargePrice::driverId,
                        Collectors.mapping(PublicChargePrice::pricePerKwh, Collectors.toList())))
                .values().stream()
                .map(prices -> percentile(prices, 0.5))
                .toList();
    }

    /** Lineare Interpolation wie {@code percentile_cont}. */
    static BigDecimal percentile(Collection<BigDecimal> values, double p) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        BigDecimal position = BigDecimal.valueOf(p).multiply(BigDecimal.valueOf(sorted.size() - 1L));
        int lower = position.intValue();
        BigDecimal fraction = position.subtract(BigDecimal.valueOf(lower));
        if (fraction.signum() == 0) return sorted.get(lower);
        BigDecimal low = sorted.get(lower);
        return low.add(sorted.get(lower + 1).subtract(low).multiply(fraction));
    }

    private static boolean plausible(BigDecimal price) {
        return price != null && price.compareTo(MIN_PLAUSIBLE) >= 0 && price.compareTo(MAX_PLAUSIBLE) <= 0;
    }

    private static BigDecimal round(BigDecimal price) {
        return price.setScale(3, RoundingMode.HALF_UP);
    }
}
