package com.evmonitor.application;

/**
 * Kennzahlen des Wochenreports: aktuelle 7 Tage gegen die 7 Tage davor.
 * {@code trialsAvailable=false}, wenn Stripe nicht konfiguriert ist (Trial-Werte dann 0).
 */
public record WeeklyReportStats(
        String weekLabel,
        String periodLabel,
        Metric newUsers,
        Metric trialsStarted,
        Metric trialsEnded,
        Metric trialsConverted,
        Metric newCars,
        Metric newEvLogs,
        Metric newTrips,
        boolean trialsAvailable
) {

    public enum Trend { UP, DOWN, FLAT }

    public record Metric(long current, long previous) {
        public long delta() {
            return current - previous;
        }

        public Trend trend() {
            if (current > previous) return Trend.UP;
            if (current < previous) return Trend.DOWN;
            return Trend.FLAT;
        }
    }

    /** Umwandlungsquote der im Fenster beendeten Trials, z. B. "50 %"; "-" ohne beendete Trials. */
    public String conversionRateLabel() {
        if (trialsEnded.current() == 0) return "-";
        return Math.round(100.0 * trialsConverted.current() / trialsEnded.current()) + " %";
    }
}
