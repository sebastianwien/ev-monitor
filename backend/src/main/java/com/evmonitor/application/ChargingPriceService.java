package com.evmonitor.application;

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

    private final JpaEvLogRepository evLogRepository;

    public ChargingReferencePrices getReferencePrices(boolean isSeedUser) {
        Object[] row = evLogRepository.findCommunityChargingPrices(isSeedUser);
        Object first = (row != null && row.length > 0) ? row[0] : null;
        Object[] stats = (first instanceof Object[]) ? (Object[]) first : row;

        BigDecimal home = resolve(stats, 0, 2, DEFAULT_HOME_PRICE);
        BigDecimal pub = resolve(stats, 1, 3, DEFAULT_PUBLIC_PRICE);
        return new ChargingReferencePrices(home, pub);
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
