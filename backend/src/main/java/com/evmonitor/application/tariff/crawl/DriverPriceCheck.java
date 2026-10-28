package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.ChargingBenchmarkCache;
import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.application.tariff.TariffApplicability;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository.PublicChargePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fahrer als Sensor für veraltete Tarife: Wo mindestens drei Fahrer mit einer Karte an einem
 * Betreiber bezahlt haben (fünf Ladungen), wird ihr Median mit der nächstliegenden gepflegten
 * Variante verglichen. Mehr als 10 % Abstand ist ein Hinweis für den Admin, nie eine Änderung:
 * Fahrer tragen die Variante nicht ein und vertippen sich.
 */
@Service
@RequiredArgsConstructor
public class DriverPriceCheck {

    static final int MIN_DRIVERS = 3;
    static final int MIN_CHARGES = 5;
    static final BigDecimal MAX_GAP = new BigDecimal("0.10");
    private static final BigDecimal MIN_PLAUSIBLE = new BigDecimal("0.15");
    private static final BigDecimal MAX_PLAUSIBLE = new BigDecimal("1.20");

    private final ChargingBenchmarkCache cache;
    private final ChargingProviderTariffRepository tariffs;
    private final CpoNameNormalizer operators;

    public record Deviation(String empName, String operator, ChargingType chargingType, String nearestVariant,
                            BigDecimal curatedPricePerKwh, BigDecimal driverMedianPricePerKwh, int drivers, int charges) {}

    private record Key(String emp, String operator, ChargingType type) {}

    public List<Deviation> deviations() {
        Map<Key, List<PublicChargePrice>> byKey = cache.publicChargePrices().stream()
                .filter(r -> r.cardEmp() != null && r.pricePerKwh() != null)
                .filter(r -> r.pricePerKwh().compareTo(MIN_PLAUSIBLE) >= 0 && r.pricePerKwh().compareTo(MAX_PLAUSIBLE) <= 0)
                .filter(r -> operators.canonicalOperator(r.cpoName()) != null)
                .collect(Collectors.groupingBy(r -> new Key(r.cardEmp(), operators.canonicalOperator(r.cpoName()), r.chargingType())));
        List<ChargingProviderTariff> valid = tariffs.findValidOn(LocalDate.now()).stream().filter(t -> !t.isDynamicPricing()).toList();
        Map<String, Optional<String>> tierCache = new HashMap<>();
        TariffApplicability.TierLookup tiers = (emp, op) -> tierCache.computeIfAbsent(emp + "\u0000" + op, k -> tariffs.findTierForCpo(emp, op));

        List<Deviation> out = new ArrayList<>();
        byKey.forEach((key, charges) -> {
            Map<UUID, List<BigDecimal>> perDriver = charges.stream().collect(Collectors.groupingBy(PublicChargePrice::driverId,
                    Collectors.mapping(PublicChargePrice::pricePerKwh, Collectors.toList())));
            if (perDriver.size() < MIN_DRIVERS || charges.size() < MIN_CHARGES) return;
            BigDecimal median = median(perDriver.values().stream().map(DriverPriceCheck::median).toList());
            List<ChargingProviderTariff> ofCard = valid.stream().filter(t -> t.getEmpName().equals(key.emp())).toList();
            TariffApplicability.applicable(ofCard, new TariffApplicability.Charge(key.operator(), key.type(), null), tiers).stream()
                    .min(Comparator.comparing(t -> t.getPricePerKwh().subtract(median).abs()))
                    .filter(nearest -> nearest.getPricePerKwh().subtract(median).abs()
                            .divide(nearest.getPricePerKwh(), 4, RoundingMode.HALF_UP).compareTo(MAX_GAP) > 0)
                    .ifPresent(nearest -> out.add(new Deviation(key.emp(), key.operator(), key.type(), nearest.getTariffVariant(),
                            nearest.getPricePerKwh(), median.setScale(3, RoundingMode.HALF_UP), perDriver.size(), charges.size())));
        });
        out.sort(Comparator.comparing(Deviation::empName).thenComparing(Deviation::operator));
        return out;
    }

    private static BigDecimal median(Collection<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
}
