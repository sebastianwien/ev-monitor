package com.evmonitor.application;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import java.util.List;

import com.evmonitor.infrastructure.external.FuelPriceService;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Derives normalized community charging reference prices (home vs public EUR/kWh) for the model
 * comparison slider. Falls back to editorial defaults when the community sample is too thin, so
 * the slider always has sane values.
 */
@Service
@RequiredArgsConstructor
public class ChargingPriceService {

    /** Editorial fallbacks (EUR/kWh) when community data is missing or too thin. */
    static final BigDecimal DEFAULT_HOME_PRICE = new BigDecimal("0.30");
    static final BigDecimal DEFAULT_PUBLIC_PRICE = new BigDecimal("0.55");
    /** Minimum priced sessions per bucket before we trust the community average over the default. */
    static final int MIN_SESSIONS = 30;

    /** The big fast-charging networks on German motorways; their contract-free price is the ad-hoc reference */
    static final List<String> FAST_NETWORKS = List.of("EnBW", "IONITY", "Fastned");

    private final JpaEvLogRepository evLogRepository;
    private final FuelPriceService fuelPriceService;
    private final ChargingProviderTariffRepository tariffRepository;

    public ChargingReferencePrices getReferencePrices(boolean isSeedUser) {
        Object[] row = evLogRepository.findCommunityChargingPrices(isSeedUser);
        Object first = (row != null && row.length > 0) ? row[0] : null;
        Object[] stats = (first instanceof Object[]) ? (Object[]) first : row;

        BigDecimal home = resolve(stats, 0, 2, DEFAULT_HOME_PRICE);
        BigDecimal pub = resolve(stats, 1, 3, DEFAULT_PUBLIC_PRICE);
        return new ChargingReferencePrices(home, pub,
                BigDecimal.valueOf(fuelPriceService.getBenzinPrice()).setScale(3, RoundingMode.HALF_UP),
                BigDecimal.valueOf(fuelPriceService.getDieselPrice()).setScale(3, RoundingMode.HALF_UP),
                FuelPriceService.COMBUSTION_LITERS_PER_100_KM,
                fastAdHocPrice());
    }

    /**
     * Fast charging without a subscription: per network the dearest current DC tariff without a
     * monthly fee (the price at the column without a contract), averaged over the networks that have one.
     */
    BigDecimal fastAdHocPrice() {
        List<BigDecimal> prices = FAST_NETWORKS.stream()
                .map(emp -> tariffRepository.findCurrentTariffsByEmp(emp).stream()
                        .filter(t -> t.getChargingType() == ChargingType.DC && t.getPricePerKwh() != null)
                        .filter(t -> t.getMonthlyFeeEur() == null || t.getMonthlyFeeEur().signum() == 0)
                        .map(ChargingProviderTariff::getPricePerKwh)
                        .max(BigDecimal::compareTo))
                .flatMap(java.util.Optional::stream)
                .toList();
        if (prices.isEmpty()) return null;
        return prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolve(Object[] stats, int valueIdx, int countIdx, BigDecimal fallback) {
        if (stats == null || stats.length <= countIdx || stats[valueIdx] == null || stats[countIdx] == null) {
            return fallback;
        }
        long count = ((Number) stats[countIdx]).longValue();
        if (count < MIN_SESSIONS) {
            return fallback;
        }
        return BigDecimal.valueOf(((Number) stats[valueIdx]).doubleValue()).setScale(4, RoundingMode.HALF_UP);
    }
}
