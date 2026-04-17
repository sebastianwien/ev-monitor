package com.evmonitor.application;

import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EnergyMeasurementType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Estimates battery State of Health from AT_VEHICLE charging logs.
 *
 * Formula per log: estimatedCapacity = kwhCharged / socDelta * 100
 * Takes the median of the last 5 qualifying logs to suppress integer-quantization
 * noise (SoC stored as whole %, so a 1% rounding error on a 64% delta ≈ 1.5% capacity error).
 *
 * Qualifying log: AT_VEHICLE + both SoC values present + delta ≥ 20%.
 * Pure static logic — no Spring, no side effects, easily unit-testable.
 */
public class BatterySohAutoDetector {

    static final int MIN_SOC_DELTA_PERCENT = 20;
    static final int ROLLING_WINDOW_SIZE = 5;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_SOH = new BigDecimal("100.00");

    private BatterySohAutoDetector() {}

    /**
     * @return SoH in percent with scale 2 (e.g. 92.02), or empty if no qualifying data
     */
    public static Optional<BigDecimal> detectSohPercent(List<EvLog> allCarLogs, BigDecimal batteryCapacityKwh) {
        if (batteryCapacityKwh == null || batteryCapacityKwh.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        List<EvLog> qualifying = allCarLogs.stream()
                .filter(BatterySohAutoDetector::isQualifying)
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        if (qualifying.isEmpty()) return Optional.empty();

        List<EvLog> window = qualifying.size() <= ROLLING_WINDOW_SIZE
                ? qualifying
                : qualifying.subList(qualifying.size() - ROLLING_WINDOW_SIZE, qualifying.size());

        List<BigDecimal> sortedCapacities = window.stream()
                .map(BatterySohAutoDetector::estimateCapacity)
                .sorted()
                .toList();

        BigDecimal medianCapacity = sortedCapacities.get(sortedCapacities.size() / 2);
        BigDecimal sohPercent = medianCapacity
                .multiply(HUNDRED)
                .divide(batteryCapacityKwh, 2, RoundingMode.HALF_UP);

        return Optional.of(sohPercent.compareTo(MAX_SOH) > 0 ? MAX_SOH : sohPercent);
    }

    static boolean isQualifying(EvLog log) {
        if (log.getMeasurementType() != EnergyMeasurementType.AT_VEHICLE) return false;
        if (log.getSocBeforeChargePercent() == null || log.getSocAfterChargePercent() == null) return false;
        if (log.getKwhCharged() == null) return false;
        return (log.getSocAfterChargePercent() - log.getSocBeforeChargePercent()) >= MIN_SOC_DELTA_PERCENT;
    }

    static BigDecimal estimateCapacity(EvLog log) {
        int delta = log.getSocAfterChargePercent() - log.getSocBeforeChargePercent();
        return log.getKwhCharged()
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(delta), 4, RoundingMode.HALF_UP);
    }
}
