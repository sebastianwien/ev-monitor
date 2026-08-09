package com.evmonitor.application;

import com.evmonitor.domain.EnergySource;
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
 *
 * The formula assumes every percentage point of SoC holds the same amount of energy, which
 * it does not - charging from 10% to 20% takes a different amount than 80% to 90%. Inferring
 * total capacity from a narrow SoC slice therefore carries a SYSTEMATIC error whose sign
 * depends on where in the range the charge happened. That is the main reason for the 75%
 * threshold: no averaging fixes a bias that always points the same way, only a wider hub
 * covering more of the pack does.
 *
 * Secondary: SoC is stored as whole percent, so the +-1% quantization error propagates
 * inversely to the hub (~3.3% capacity error at 30%, ~1.3% at 75%). With entries persisted
 * from a 2% change, small hubs would let rounding noise alone create them.
 *
 * Two mechanisms suppress the remaining noise:
 *   1. A rolling window of the last 5 qualifying logs.
 *   2. A hub-weighted median over that window: each estimate carries its SoC hub as
 *      weight, so a 95% charge outvotes a 75% one. Weighted median (not mean) keeps
 *      a single broken estimate from dragging the result.
 *
 * Qualifying log: AT_VEHICLE + both SoC values present + hub >= 75%.
 * Pure static logic - no Spring, no side effects, easily unit-testable.
 */
public class BatterySohAutoDetector {

    static final int MIN_SOC_DELTA_PERCENT = 75;
    static final int ROLLING_WINDOW_SIZE = 5;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal MAX_SOH = new BigDecimal("100.00");

    /** A capacity estimate together with the SoC hub it was derived from. */
    private record Estimate(BigDecimal capacityKwh, BigDecimal socHub) {}

    /**
     * @param sohPercent SoH in percent with scale 2 (e.g. 92.02)
     * @param sampleSize charging logs behind the estimate - surfaced to the user so a
     *                   value backed by one charge is not read like one backed by five
     * @param socHubPercent SoC hub of the charge that carried the weighted median, i.e. the
     *                      one the displayed value actually rests on
     */
    public record Detection(BigDecimal sohPercent, int sampleSize, BigDecimal socHubPercent) {}

    private BatterySohAutoDetector() {}

    /**
     * @return the detected SoH with its sample size, or empty if no qualifying data
     */
    public static Optional<Detection> detect(List<EvLog> allCarLogs, BigDecimal batteryCapacityKwh) {
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

        Estimate median = weightedMedian(window);
        BigDecimal sohPercent = median.capacityKwh()
                .multiply(HUNDRED)
                .divide(batteryCapacityKwh, 2, RoundingMode.HALF_UP);

        return Optional.of(new Detection(
                sohPercent.compareTo(MAX_SOH) > 0 ? MAX_SOH : sohPercent,
                window.size(),
                median.socHub()));
    }

    /**
     * Weighted median: estimates sorted ascending by capacity, weighted by their SoC hub.
     * Returns the first estimate at which the cumulative weight reaches half the total.
     * With equal hubs this degenerates to the classic median.
     */
    private static Estimate weightedMedian(List<EvLog> window) {
        List<Estimate> sorted = window.stream()
                .map(log -> new Estimate(estimateCapacity(log), socHub(log)))
                .sorted(Comparator.comparing(Estimate::capacityKwh))
                .toList();

        BigDecimal totalWeight = sorted.stream()
                .map(Estimate::socHub)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cumulative = BigDecimal.ZERO;
        for (Estimate estimate : sorted) {
            cumulative = cumulative.add(estimate.socHub());
            if (cumulative.multiply(TWO).compareTo(totalWeight) >= 0) {
                return estimate;
            }
        }
        return sorted.get(sorted.size() - 1);
    }

    static boolean isQualifying(EvLog log) {
        if (!log.isIncludeInStatistics()) return false;
        // SOC_INFERRED logs would create a self-referential loop: their kwh value was
        // computed from SoC-delta x effective_capacity, so feeding it back into SoH
        // detection yields the very capacity that was assumed (drifting by brutto/netto
        // ratio per run). NULL stays trusted for backwards-compat with pre-V119 rows.
        if (log.getEnergySource() == EnergySource.SOC_INFERRED) return false;
        boolean hasVehicleKwh = log.getMeasurementType() == EnergyMeasurementType.AT_VEHICLE
                || log.getKwhAtVehicle() != null;
        if (!hasVehicleKwh) return false;
        if (log.getSocBeforeChargePercent() == null || log.getSocAfterChargePercent() == null) return false;
        if (effectiveKwh(log) == null) return false;
        return socHub(log).compareTo(BigDecimal.valueOf(MIN_SOC_DELTA_PERCENT)) >= 0;
    }

    static BigDecimal estimateCapacity(EvLog log) {
        return effectiveKwh(log)
                .multiply(HUNDRED)
                .divide(socHub(log), 4, RoundingMode.HALF_UP);
    }

    /** SoC percentage points added during the charge - the estimate's precision driver. */
    private static BigDecimal socHub(EvLog log) {
        return log.getSocAfterChargePercent().subtract(log.getSocBeforeChargePercent());
    }

    private static BigDecimal effectiveKwh(EvLog log) {
        return log.getKwhAtVehicle() != null ? log.getKwhAtVehicle() : log.getKwhCharged();
    }
}
