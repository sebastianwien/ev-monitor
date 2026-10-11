package com.evmonitor.application.personalrange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * How far the community's measured consumption lies above WLTP, as the median ratio over all
 * models with enough cars. Replaces fixed guesses for models without own measurements, so the
 * estimate follows the data as more people log. Median, not a car-weighted mean: two popular
 * models would otherwise define "the fleet", and one outlier model cannot pull it.
 *
 * <p>Measured consumption is battery side, WLTP is at the plug: the ratio maps WLTP onto our
 * measurement basis, it is not a pure driving-efficiency gap.
 *
 * <p>A factor is null when fewer than MIN_MODELS models have the value; callers fall back to
 * their fixed factor.
 */
public record WltpFleetFactors(
        BigDecimal overall, int overallModels,
        BigDecimal summer, int summerModels,
        BigDecimal winter, int winterModels
) {
    static final int MIN_CARS_PER_MODEL = 5;
    static final int MIN_MODELS = 10;

    /** One model: car count, WLTP and the community consumption values (null when missing). */
    public record Sample(int carCount, BigDecimal wltp, BigDecimal overall, BigDecimal summer, BigDecimal winter) {}

    public static WltpFleetFactors from(List<Sample> models) {
        List<Sample> usable = models.stream()
                .filter(m -> m.carCount() >= MIN_CARS_PER_MODEL && m.wltp() != null && m.wltp().signum() > 0)
                .toList();
        List<BigDecimal> overall = ratios(usable, Sample::overall);
        List<BigDecimal> summer = ratios(usable, Sample::summer);
        List<BigDecimal> winter = ratios(usable, Sample::winter);
        return new WltpFleetFactors(median(overall), overall.size(), median(summer), summer.size(),
                median(winter), winter.size());
    }

    private static List<BigDecimal> ratios(List<Sample> models, Function<Sample, BigDecimal> value) {
        return models.stream()
                .filter(m -> value.apply(m) != null)
                .map(m -> value.apply(m).divide(m.wltp(), 4, RoundingMode.HALF_UP))
                .sorted()
                .toList();
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        if (sorted.size() < MIN_MODELS) return null;
        int mid = sorted.size() / 2;
        BigDecimal median = sorted.size() % 2 == 1 ? sorted.get(mid)
                : sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        return Objects.requireNonNull(median).setScale(2, RoundingMode.HALF_UP);
    }
}
