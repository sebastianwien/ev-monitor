package com.evmonitor.application.consumption;

import com.evmonitor.application.ConsumptionResult;
import com.evmonitor.application.PlausibilityProperties;
import com.evmonitor.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * Verbrauchsberechnung und Plausibilitätsprüfung für EV-Ladevorgänge.
 *
 * <p>Enthält die gesamte Berechnungslogik, die zuvor in {@code EvLogService} eingebettet war:
 * SoC-Delta-Formel, Zwei-Pass-Algorithmus (Rohwerte + Plausibilität), kWh-Normalisierung
 * (AT_CHARGER ↔ AT_VEHICLE), Distanz- und Kapazitäts-Lookups.
 *
 * <p>Diese Klasse hat <b>keine CRUD-Seiteneffekte</b> - sie liest Daten und liefert Ergebnisse.
 * Alle Methoden sind threadsafe (keine mutable Zustandshaltung).
 */
@Service
@RequiredArgsConstructor
public class ConsumptionCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final VehicleSpecificationRepository vehicleSpecificationRepository;
    private final PlausibilityProperties plausibility;
    private final BatterySohRepository batterySohRepository;

    // -------------------------------------------------------------------------
    // kWh-Normalisierung
    // -------------------------------------------------------------------------

    /**
     * Normalizes kwhCharged to AT_VEHICLE level for consumption calculations.
     * AT_CHARGER data is multiplied by the charging efficiency factor (AC: 0.90, DC: 0.95)
     * to remove charging losses from the consumption metric — matching the SoC-delta perspective.
     * AT_VEHICLE data (Smartcar, Tesla Live) is already at battery level and returned as-is.
     */
    public BigDecimal effectiveKwhForConsumption(EvLog log) {
        if (log.getMeasurementType() == EnergyMeasurementType.AT_VEHICLE) return log.getKwhCharged();
        return log.getKwhCharged().multiply(BigDecimal.valueOf(chargingEfficiency(log)));
    }

    /**
     * Normalizes kwhCharged to AT_CHARGER level for cost calculations.
     * AT_VEHICLE data is divided by the efficiency factor to get the grid-side equivalent —
     * because cost_eur reflects what was billed at the charger, not what entered the battery.
     * AT_CHARGER data is returned as-is.
     */
    public BigDecimal effectiveKwhForCost(EvLog log) {
        if (log.getMeasurementType() == EnergyMeasurementType.AT_CHARGER) return log.getKwhCharged();
        return log.getKwhCharged().divide(BigDecimal.valueOf(chargingEfficiency(log)), 4, RoundingMode.HALF_UP);
    }

    /**
     * Returns the charging efficiency factor for a log.
     * DC fast charging: 0.95 (5% loss). AC: 0.90 (10% loss).
     * When charging type is unknown, is_public_charging is used as proxy (public → DC, private → AC).
     */
    public double chargingEfficiency(EvLog log) {
        if (log.getChargingType() == ChargingType.DC) return plausibility.getDcChargingEfficiency();
        if (log.getChargingType() == ChargingType.AC) return plausibility.getAcChargingEfficiency();
        return log.isPublicCharging() ? plausibility.getDcChargingEfficiency() : plausibility.getAcChargingEfficiency();
    }

    // -------------------------------------------------------------------------
    // Verbrauchsberechnung - Kern
    // -------------------------------------------------------------------------

    /**
     * Calculates consumption (kWh/100km) for the trip from logX to logY.
     *
     * logX (trip start): logX.canBeUsedAsLogX() must be true
     *                    requires: odometer + socAfterChargePercent
     * logY (trip end):   logY.isComplete() must be true
     *                    requires: odometer + kwhCharged + socAfterChargePercent
     *
     * Formula (kWh-primary, when kwhCharged is available):
     *   energyConsumed = effectiveKwh(logY) + (socAfter(logX) - socAfter(logY)) * batteryCapacity / 100
     *   consumption    = energyConsumed / distance * 100
     *
     * The SoC-delta term corrects for net battery level differences between sessions.
     * When both sessions end at the same SoC, it is zero and only kWh drives the result.
     * Fallback (no kwhCharged): energyConsumed = (socAfter(logX) - socBefore(logY)) * capacity / 100
     *
     * @param batteryCapacityKwh MUST be the effective (SoH-adjusted) capacity, not the nominal spec.
     *                           The SoC-delta correction term is capacity-sensitive: passing nominal
     *                           inflates the correction proportionally to the SoH deficit.
     *                           Use buildCapacityLookup() or Car.getEffectiveBatteryCapacityKwhAt().
     * @return consumption in kWh/100km, or empty if data is insufficient or result is invalid
     */
    public Optional<BigDecimal> calculateConsumption(EvLog logX, EvLog logY, BigDecimal batteryCapacityKwh) {
        return calculateConsumption(logX, logY, batteryCapacityKwh, BigDecimal.ZERO);
    }

    /**
     * @param intermediateKwh kWh charged by transparent intermediate logs (e.g. go-e sub-sessions)
     *                        between logX and logY. Added to the SoC-delta energy to get total
     *                        energy consumed over the full distance logX→logY.
     */
    public Optional<BigDecimal> calculateConsumption(EvLog logX, EvLog logY, BigDecimal batteryCapacityKwh,
            BigDecimal intermediateKwh) {
        if (!logX.canBeUsedAsLogX() || !logY.isComplete()) return Optional.empty();
        if (batteryCapacityKwh == null || batteryCapacityKwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        int distance = logY.getOdometerKm() - logX.getOdometerKm();
        if (distance <= 0) return Optional.empty();

        // kWh-primary: when kwhCharged is available it drives the calculation directly.
        // The SoC-delta term (socAfter_X - socAfter_Y) corrects for net battery level differences
        // between sessions — it is zero when both sessions end at the same SoC.
        // This avoids the SoH bias of the old socBefore-primary approach, where BMS SOC percentages
        // (calibrated to degraded real capacity) were multiplied by nominal battery capacity.
        // Fallback to pure SoC-delta only when kwhCharged is absent (theoretical — isComplete()
        // already requires kwhCharged, so this branch is unreachable via calculateConsumptionPerLog).
        final BigDecimal energyConsumedKwh;
        if (logY.hasKwhCharged()) {
            BigDecimal socDeltaCorrection = BigDecimal.valueOf(logX.getSocAfterChargePercent())
                    .subtract(BigDecimal.valueOf(logY.getSocAfterChargePercent()))
                    .multiply(batteryCapacityKwh)
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP);
            energyConsumedKwh = effectiveKwhForConsumption(logY)
                    .add(socDeltaCorrection)
                    .add(intermediateKwh);
        } else {
            if (logY.getSocBeforeChargePercent() == null) return Optional.empty();
            energyConsumedKwh = BigDecimal.valueOf(logX.getSocAfterChargePercent())
                    .subtract(BigDecimal.valueOf(logY.getSocBeforeChargePercent()))
                    .multiply(batteryCapacityKwh)
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP)
                    .add(intermediateKwh);
        }

        if (energyConsumedKwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        return Optional.of(energyConsumedKwh
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(distance), 2, RoundingMode.HALF_UP));
    }

    // -------------------------------------------------------------------------
    // Zwei-Pass-Algorithmus
    // -------------------------------------------------------------------------

    /**
     * Calculates per-log consumption (kWh/100km) for each complete log, with a plausibility verdict.
     *
     * Two-pass algorithm:
     *   Pass 1 — compute raw consumption for every complete log (isComplete + previous log with odometer).
     *            Trips shorter than minTripDistanceKm are excluded (unreliable odometer data).
     *   Pass 2 — check each value against the full distribution + WLTP reference via isConsumptionPlausible().
     *
     * @return map of logId (logY) → ConsumptionResult(value, plausible, distanceKm)
     */
    public Map<UUID, ConsumptionResult> calculateConsumptionPerLog(List<EvLog> allLogs, BigDecimal batteryCapacityKwh, BigDecimal wltpKwh) {
        return calculateConsumptionPerLog(allLogs, date -> batteryCapacityKwh, wltpKwh);
    }

    public Map<UUID, ConsumptionResult> calculateConsumptionPerLog(List<EvLog> allLogs, Function<LocalDate, BigDecimal> capacityForDate, BigDecimal wltpKwh) {
        // Always sort — correctness must not depend on caller discipline
        List<EvLog> sorted = allLogs.stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        // Pass 1: raw consumptions
        List<UUID> ids = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<Integer> distances = new ArrayList<>();

        sorted.stream()
                .filter(EvLog::isComplete)
                .forEach(logY -> {
                    PreviousLogResult prev = findPreviousLog(sorted, logY);
                    if (prev == null) return;
                    int dist = logY.getOdometerKm() - prev.logX().getOdometerKm();
                    if (dist < plausibility.getMinTripDistanceKm()) return;
                    BigDecimal capacity = capacityForDate.apply(logY.getLoggedAt().toLocalDate());
                    if (capacity == null) return;
                    calculateConsumption(prev.logX(), logY, capacity, prev.intermediateKwh()).ifPresent(c -> {
                        ids.add(logY.getId());
                        values.add(c);
                        distances.add(dist);
                    });
                });

        // Pass 2: plausibility check — absolute bounds + statistical/WLTP reference
        Map<UUID, ConsumptionResult> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            boolean plausible = isConsumptionPlausible(values.get(i), values, wltpKwh);
            result.put(ids.get(i), new ConsumptionResult(values.get(i), plausible, distances.get(i)));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Plausibilitätsprüfung
    // -------------------------------------------------------------------------

    /**
     * Checks whether a calculated consumption value is plausible for this car.
     *
     * Three layers:
     *   Layer 1 — Absolute sanity bounds: [absoluteMin, absoluteMax] kWh/100km (always applied).
     *   Layer 2a — Statistical check (≥ minTripsForStatistical trips):
     *              mean(history) ± sigmaMultiplier × stdDev(history).
     *   Layer 2b — WLTP bootstrap (< minTripsForStatistical, WLTP available):
     *              [WLTP × wltpLowerFactor, WLTP × wltpUpperFactor].
     *   Layer 2c — Only absolute bounds apply (no history, no WLTP).
     *
     * Implausible values likely indicate a missing charging session between logX and logY.
     *
     * @param consumptionKwhPer100km     the calculated value to check
     * @param historicalConsumptions     all computed consumptions for this car (may include self)
     * @param wltpConsumptionKwhPer100km the car's WLTP reference value (nullable)
     */
    public boolean isConsumptionPlausible(BigDecimal consumptionKwhPer100km,
                                          List<BigDecimal> historicalConsumptions,
                                          BigDecimal wltpConsumptionKwhPer100km) {
        // Layer 1: absolute sanity bounds
        BigDecimal absMin = BigDecimal.valueOf(plausibility.getAbsoluteMinKwhPer100km());
        BigDecimal absMax = BigDecimal.valueOf(plausibility.getAbsoluteMaxKwhPer100km());
        if (consumptionKwhPer100km.compareTo(absMin) < 0) return false;
        if (consumptionKwhPer100km.compareTo(absMax) > 0) return false;

        List<BigDecimal> history = historicalConsumptions != null ? historicalConsumptions : List.of();

        // Layer 2a: statistical check
        if (history.size() >= plausibility.getMinTripsForStatistical()) {
            BigDecimal mean = ConsumptionMath.mean(history);
            BigDecimal stdDev = ConsumptionMath.stdDev(history, mean);
            if (stdDev.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margin = stdDev.multiply(BigDecimal.valueOf(plausibility.getSigmaMultiplier()));
                return consumptionKwhPer100km.compareTo(mean.subtract(margin)) >= 0
                        && consumptionKwhPer100km.compareTo(mean.add(margin)) <= 0;
            }
            // stdDev == 0: all values identical — accept only if within 10% of mean
            BigDecimal tolerance = mean.multiply(new BigDecimal("0.10"));
            return consumptionKwhPer100km.subtract(mean).abs().compareTo(tolerance) <= 0;
        }

        // Layer 2b: WLTP bootstrap
        if (wltpConsumptionKwhPer100km != null && wltpConsumptionKwhPer100km.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lower = wltpConsumptionKwhPer100km.multiply(BigDecimal.valueOf(plausibility.getWltpLowerFactor()));
            BigDecimal upper = wltpConsumptionKwhPer100km.multiply(BigDecimal.valueOf(plausibility.getWltpUpperFactor()));
            return consumptionKwhPer100km.compareTo(lower) >= 0
                    && consumptionKwhPer100km.compareTo(upper) <= 0;
        }

        // Layer 2c: only absolute bounds (already passed above)
        return true;
    }

    /**
     * Fallback consumption calculation when SoC data is missing.
     * Simple formula: (total kWh charged / total distance) × 100
     * Less accurate for partial charging but works when SoC is unavailable.
     */
    public BigDecimal calculateConsumptionFallback(List<EvLog> logs, BigDecimal totalDistanceKm) {
        if (totalDistanceKm == null || totalDistanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal totalKwh = logs.stream()
                .map(this::effectiveKwhForConsumption)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalKwh
                .multiply(HUNDRED)
                .divide(totalDistanceKm, 2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Lookup und Helper
    // -------------------------------------------------------------------------

    /**
     * Looks up the WLTP combined consumption for the given car via VehicleSpecification.
     * Returns null if no spec exists (e.g. model not yet in the database).
     *
     * Intentionally uses nominal battery capacity (not effective) for the lookup:
     * WLTP specs are standardized per nominal battery size. Battery degradation affects
     * the actual energy drawn, but the WLTP reference value stays tied to the nominal spec.
     */
    public BigDecimal lookupWltp(Car car) {
        if (car.getModel() == null || car.getBatteryCapacityKwh() == null) return null;
        return vehicleSpecificationRepository
                .findByCarBrandAndModelAndCapacityAndType(
                        car.getModel().getBrand().name(),
                        car.getModel().name(),
                        car.getBatteryCapacityKwh(),
                        VehicleSpecification.WltpType.COMBINED)
                .map(VehicleSpecification::getOfficialConsumptionKwhPer100km)
                .orElse(null);
    }

    /**
     * Compute distance driven before each charge from consecutive odometer readings.
     * Distance for log[i] = log[i].odometer - log[j].odometer where j is the most recent log with odometer data.
     * Skips logs without odometer data to find the actual previous trip.
     * Logs must be sorted by loggedAt ascending.
     */
    public Map<UUID, Integer> computeDistanceByLogId(List<EvLog> sortedLogs) {
        Map<UUID, Integer> result = new java.util.HashMap<>();
        for (int i = 1; i < sortedLogs.size(); i++) {
            EvLog current = sortedLogs.get(i);
            if (current.getOdometerKm() == null) {
                continue; // Skip logs without odometer
            }

            // Find previous log with odometer data (search backwards)
            EvLog previous = null;
            for (int j = i - 1; j >= 0; j--) {
                if (sortedLogs.get(j).getOdometerKm() != null) {
                    previous = sortedLogs.get(j);
                    break;
                }
            }

            if (previous != null) {
                int distance = current.getOdometerKm() - previous.getOdometerKm();
                if (distance > 0) {
                    result.put(current.getId(), distance);
                }
            }
        }
        return result;
    }

    /**
     * Baut eine datum-bewusste Kapazitäts-Lookup-Funktion für ein Auto.
     * Nutzt SoH-History wenn vorhanden, sonst Fallback auf batteryDegradationPercent.
     */
    public Function<LocalDate, BigDecimal> buildCapacityLookup(Car car) {
        if (car.getBatteryCapacityKwh() == null) return date -> null;
        List<BatterySohEntry> history = batterySohRepository.findByCarId(car.getId());
        return date -> car.getEffectiveBatteryCapacityKwhAt(date, history);
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    /** logX candidate + kWh accumulated from transparent intermediate logs. */
    private record PreviousLogResult(EvLog logX, BigDecimal intermediateKwh) {}

    /**
     * Searches backwards for the nearest valid logX predecessor of logY.
     *
     * Transparent logs (e.g. WALLBOX_GOE sub-sessions without SoC) are skipped:
     * their kWh is accumulated in intermediateKwh so it can be added to the
     * SoC-delta energy in calculateConsumption().
     *
     * Non-transparent logs that fail canBeUsedAsLogX() (e.g. a manual log without SoC)
     * still break the chain — they represent a real data gap.
     *
     * Callers must pass a list sorted ascending by loggedAt.
     * calculateConsumptionPerLog() guarantees this internally.
     */
    private PreviousLogResult findPreviousLog(List<EvLog> sortedLogs, EvLog logY) {
        for (int i = 0; i < sortedLogs.size(); i++) {
            if (!sortedLogs.get(i).getId().equals(logY.getId())) continue;
            if (i == 0) return null;

            BigDecimal intermediateKwh = BigDecimal.ZERO;
            for (int j = i - 1; j >= 0; j--) {
                EvLog candidate = sortedLogs.get(j);
                if (candidate.canBeUsedAsLogX()) {
                    return new PreviousLogResult(candidate, intermediateKwh);
                }
                if (candidate.getDataSource().isTransparentForConsumptionChain()) {
                    if (candidate.hasKwhCharged()) {
                        intermediateKwh = intermediateKwh.add(candidate.getKwhCharged());
                    }
                    // transparent → weiter zurückgehen
                } else {
                    return null; // echter Kettenbruch (manueller Log ohne SoC)
                }
            }
            return null;
        }
        return null;
    }
}
