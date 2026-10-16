package com.evmonitor.application.charging;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fast-charge power of every model in one place: the model's own measurement from MIN_SESSIONS
 * qualifying DC sessions on, otherwise the datasheet value the caller passes, otherwise the median of
 * the measured models, otherwise DEFAULT_KW.
 * Built from one query over all models (PublicModelService.getFastChargePowerTable, cached).
 */
public final class FastChargePowerTable {

    /**
     * Below this the 75th percentile tips on a single outlier. Set with the measurement on 07.10.2026;
     * on 12.10.2026 it leaves 28 models (322 cars) measured, 5 sessions would add 4 models.
     */
    public static final int MIN_SESSIONS = 8;

    /** One model's p75 over its qualifying DC sessions and their number. */
    public record Measurement(String model, BigDecimal kw, int sessions) {}

    private final Map<String, Measurement> byModel;
    private final FastChargePower fleet;

    private FastChargePowerTable(Map<String, Measurement> byModel, FastChargePower fleet) {
        this.byModel = byModel;
        this.fleet = fleet;
    }

    public static FastChargePowerTable from(List<Measurement> rows) {
        Map<String, Measurement> byModel = rows.stream()
                .filter(r -> r.model() != null && r.kw() != null)
                .collect(Collectors.toMap(Measurement::model, Function.identity(), (a, b) -> a));
        List<BigDecimal> measured = byModel.values().stream()
                .filter(m -> m.sessions() >= MIN_SESSIONS)
                .map(m -> scale1(m.kw()))
                .sorted()
                .toList();
        FastChargePower fleet = measured.isEmpty() ? FastChargePower.fallback()
                : new FastChargePower(median(measured), FastChargePower.Source.FLEET_MEDIAN, 0);
        return new FastChargePowerTable(Map.copyOf(byModel), fleet);
    }

    /** Never null: the model's measurement, else the fleet median, else the fixed default. */
    public FastChargePower forModel(String modelEnumName) {
        return forModel(modelEnumName, null);
    }

    /** Never null: the model's measurement, else its datasheet value, else the fleet median, else the fixed default. */
    public FastChargePower forModel(String modelEnumName, BigDecimal datasheetKw) {
        Measurement m = byModel.get(modelEnumName);
        int sessions = m != null ? m.sessions() : 0;
        if (sessions >= MIN_SESSIONS) return FastChargePower.measured(scale1(m.kw()), sessions);
        if (datasheetKw != null) return new FastChargePower(scale1(datasheetKw), FastChargePower.Source.SPEC, sessions);
        return new FastChargePower(fleet.kw(), fleet.source(), sessions);
    }

    /** The model's own measurement only, null when it has too few sessions: for places that show "gemessen". */
    public BigDecimal measuredKw(String modelEnumName) {
        FastChargePower p = forModel(modelEnumName);
        return p.isMeasured() ? p.kw() : null;
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        int mid = sorted.size() / 2;
        BigDecimal median = sorted.size() % 2 == 1 ? sorted.get(mid)
                : sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.TWO, 1, RoundingMode.HALF_UP);
        return scale1(median);
    }

    private static BigDecimal scale1(BigDecimal kw) {
        return kw.setScale(1, RoundingMode.HALF_UP);
    }
}
