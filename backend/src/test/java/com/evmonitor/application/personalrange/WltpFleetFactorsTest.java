package com.evmonitor.application.personalrange;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WltpFleetFactorsTest {

    private static WltpFleetFactors.Sample sample(int cars, String avg, String summer, String winter) {
        return sample(cars, new BigDecimal(avg), new BigDecimal(summer), winter != null ? new BigDecimal(winter) : null);
    }

    private static WltpFleetFactors.Sample sample(int cars, BigDecimal avg, BigDecimal summer, BigDecimal winter) {
        return new WltpFleetFactors.Sample(cars, new BigDecimal("10.0"), avg, summer, winter);
    }

    /** n models on WLTP 10: overall 12 + i/10, summer 11 + i/10, winter 15 + i/10 */
    private static List<WltpFleetFactors.Sample> fleet(int n) {
        List<WltpFleetFactors.Sample> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal step = BigDecimal.valueOf(i, 1);
            list.add(sample(5, new BigDecimal("12").add(step), new BigDecimal("11").add(step), new BigDecimal("15").add(step)));
        }
        return list;
    }

    @Test
    void medianRatioPerSeasonFromModelsWithEnoughCars() {
        WltpFleetFactors f = WltpFleetFactors.from(fleet(11));

        // overall ratios 1.20 .. 1.30 → median of 11 = 6th value
        assertThat(f.overall()).isEqualByComparingTo("1.25");
        assertThat(f.summer()).isEqualByComparingTo("1.15");
        assertThat(f.winter()).isEqualByComparingTo("1.55");
        assertThat(f.winterModels()).isEqualTo(11);
    }

    @Test
    void modelsWithFewCarsDoNotCountAndOutliersDoNotPullTheMedian() {
        List<WltpFleetFactors.Sample> list = new ArrayList<>(fleet(10));
        list.add(sample(4, "30.0", "30.0", "30.0"));   // too few cars
        list.add(sample(50, "99.0", "99.0", "99.0"));  // heavy outlier with many cars

        WltpFleetFactors f = WltpFleetFactors.from(list);

        // 11 counted models: 10 regular + outlier → median is the 6th smallest = 1.25
        assertThat(f.overall()).isEqualByComparingTo("1.25");
        assertThat(f.overallModels()).isEqualTo(11);
    }

    @Test
    void belowTenModelsAFactorIsMissing() {
        List<WltpFleetFactors.Sample> list = new ArrayList<>(fleet(10));
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) list.set(i, sample(5, "12.0", "11.0", null));
        }

        WltpFleetFactors f = WltpFleetFactors.from(list);

        assertThat(f.overall()).isNotNull();
        assertThat(f.winter()).isNull();
        assertThat(f.winterModels()).isEqualTo(1);
    }
}
