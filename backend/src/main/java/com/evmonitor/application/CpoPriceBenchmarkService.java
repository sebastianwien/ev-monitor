package com.evmonitor.application;

import com.evmonitor.application.CpoPriceBenchmark.CardOffer;
import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository.PublicChargePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
    static final int WINDOW_MONTHS = 12;

    private final ChargingBenchmarkQueryRepository queryRepository;
    private final ChargingProviderTariffRepository tariffRepository;
    private final CpoNameNormalizer cpoNameNormalizer;

    public Optional<CpoPriceBenchmark> benchmark(String cpo, ChargingType type, UUID requesterId) {
        String operator = cpoNameNormalizer.canonicalOperator(cpo);
        if (operator == null || type == null || type == ChargingType.UNKNOWN) return Optional.empty();

        List<PublicChargePrice> others = queryRepository.publicChargePrices(LocalDateTime.now().minusMonths(WINDOW_MONTHS)).stream()
                .filter(r -> r.chargingType() == type)
                .filter(r -> !r.userId().equals(requesterId))
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

    /** Gepflegt ersetzt die Fahrerkarte desselben Anbieters - der Tarif kennt Variante und Grundgebuehr. */
    private List<CardOffer> cards(String operator, ChargingType type, List<PublicChargePrice> others) {
        List<CardOffer> curated = curatedOffers(operator, type);
        Set<String> curatedEmps = curated.stream().map(CardOffer::name).collect(Collectors.toSet());
        Stream<CardOffer> byDrivers = driverCards(others).stream()
                .filter(c -> c.emp() == null || !curatedEmps.contains(c.emp()))
                .map(DriverCard::offer);
        return Stream.concat(curated.stream(), byDrivers)
                .sorted(Comparator.comparing(CardOffer::pricePerKwh)
                        .thenComparing(CardOffer::source, Comparator.reverseOrder())
                        .thenComparing(CardOffer::name))
                .limit(MAX_CARDS)
                .toList();
    }

    private List<DriverCard> driverCards(List<PublicChargePrice> others) {
        Map<String, List<PublicChargePrice>> byCard = others.stream()
                .filter(r -> r.cardName() != null && !r.cardName().isBlank())
                .collect(Collectors.groupingBy(r -> r.cardName().trim().toLowerCase()));
        return byCard.values().stream()
                .map(rows -> {
                    List<BigDecimal> perDriver = driverMedians(rows);
                    if (perDriver.size() < MIN_CARD_DRIVERS) return null;
                    CardOffer offer = new CardOffer(mostCommonSpelling(rows), null, round(percentile(perDriver, 0.5)),
                            null, CardOffer.Source.DRIVERS, perDriver.size(), null);
                    String emp = rows.stream().map(PublicChargePrice::cardEmp).filter(Objects::nonNull).findFirst().orElse(null);
                    return new DriverCard(emp, offer);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<CardOffer> curatedOffers(String operator, ChargingType type) {
        Map<String, Optional<String>> tierByEmp = new HashMap<>();
        return tariffRepository.findValidOn(LocalDate.now()).stream()
                .filter(t -> t.getChargingType() == type && t.getPricePerKwh() != null)
                .filter(t -> appliesAt(t, operator, tierByEmp))
                .map(t -> new CardOffer(t.getEmpName(), t.getTariffVariant(), round(t.getPricePerKwh()), t.getMonthlyFeeEur(),
                        CardOffer.Source.CURATED, null,
                        t.getLastVerifiedAt() == null ? null : t.getLastVerifiedAt().toLocalDate()))
                .toList();
    }

    /**
     * Ein Tarif mit Betreiber gilt nur dort; mit Tier, wo das Mapping diesen Tier nennt (ohne
     * Mapping nicht - das waere geraten); sonst nur im eigenen Netz.
     */
    private boolean appliesAt(ChargingProviderTariff t, String operator, Map<String, Optional<String>> tierByEmp) {
        if (t.getCpoName() != null) return t.getCpoName().equals(operator);
        if (t.getPriceTier() != null) {
            return tierByEmp.computeIfAbsent(t.getEmpName(), emp -> tariffRepository.findTierForCpo(emp, operator))
                    .map(t.getPriceTier()::equals)
                    .orElse(false);
        }
        return t.getEmpName().equals(operator);
    }

    private static List<BigDecimal> driverMedians(List<PublicChargePrice> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(PublicChargePrice::userId,
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

    /** Die haeufigste Schreibweise, bei Gleichstand die alphabetisch erste. */
    private static String mostCommonSpelling(List<PublicChargePrice> rows) {
        return rows.stream()
                .map(r -> r.cardName().trim())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    private static boolean plausible(BigDecimal price) {
        return price != null && price.compareTo(MIN_PLAUSIBLE) >= 0 && price.compareTo(MAX_PLAUSIBLE) <= 0;
    }

    private static BigDecimal round(BigDecimal price) {
        return price.setScale(3, RoundingMode.HALF_UP);
    }

    private record DriverCard(String emp, CardOffer offer) {}
}
