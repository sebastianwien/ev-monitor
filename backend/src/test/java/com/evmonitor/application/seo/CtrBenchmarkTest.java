package com.evmonitor.application.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CtrBenchmarkTest {

    @Test
    void expectedCtrFallsWithPosition() {
        assertThat(CtrBenchmark.expected(1.0)).isEqualTo(0.28);
        assertThat(CtrBenchmark.expected(7.9)).isEqualTo(0.03);
        assertThat(CtrBenchmark.expected(14.0)).isEqualTo(0.01);
        assertThat(CtrBenchmark.expected(35.0)).isEqualTo(0.005);
        assertThat(CtrBenchmark.expected(0.0)).isZero();
    }

    @Test
    void potentialIsNeverNegative() {
        assertThat(CtrBenchmark.potential(1000, 30, 0.03)).isZero();
        assertThat(CtrBenchmark.potential(1000, 3, 0.03)).isEqualTo(27);
        assertThat(CtrBenchmark.potential(0, 0, 0.03)).isZero();
    }
}
