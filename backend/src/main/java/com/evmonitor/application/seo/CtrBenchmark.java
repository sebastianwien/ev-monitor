package com.evmonitor.application.seo;

/**
 * Übliche organische CTR je Google-Position, gerundete Branchenwerte (CTR-Kurven aus öffentlichen Studien), keine
 * eigenen Daten. Dient als Maßstab für "Low CTR" und für das geschätzte Klick-Potenzial; ein relativer Maßstab
 * (Median der eigenen Seiten) versagt, sobald die meisten Seiten ohne Klick sind.
 */
public final class CtrBenchmark {

    /** Index = gerundete Position 1-10. */
    private static final double[] TOP_TEN = {0.28, 0.15, 0.11, 0.08, 0.06, 0.045, 0.035, 0.03, 0.025, 0.02};
    private static final double PAGE_TWO = 0.01;
    private static final double BEYOND = 0.005;

    private CtrBenchmark() {
    }

    public static double expected(double position) {
        if (position <= 0) return 0;
        long rank = Math.max(1, Math.round(position));
        if (rank <= TOP_TEN.length) return TOP_TEN[(int) rank - 1];
        return rank <= 20 ? PAGE_TWO : BEYOND;
    }

    /** Zusätzliche Klicks, wenn die übliche CTR erreicht würde; nie negativ. */
    public static long potential(long impressions, long clicks, double expectedCtr) {
        return Math.max(0, Math.round(impressions * expectedCtr) - clicks);
    }
}
