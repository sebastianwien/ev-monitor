package com.evmonitor.application.weather;

import java.util.List;
import java.util.Optional;

/**
 * Temperatur-Ausblick am Heimladeort: Mittel der letzten Tage gegen Mittel der kommenden Tage.
 * Negative Differenz heisst, es wird kaelter. Dazu Tiefst- und Hoechstwert der kommenden Tage
 * ({@code null} ohne Werte). Grad Celsius, ungerundet.
 */
public record WeatherOutlook(double recentMeanC, double aheadMeanC, double deltaC, int recentDays, int aheadDays,
                             Double aheadMinC, Double aheadMaxC) {

    /**
     * @param dailyMeans Tagesmittel, Index 0 der aelteste Tag, die ersten {@code pastDays} liegen
     *                   in der Vergangenheit, der Rest ist Vorhersage. Luecken ({@code null})
     *                   werden uebersprungen.
     * @param dailyMins  Tiefstwerte in derselben Reihenfolge, darf fehlen ({@code null})
     * @param dailyMaxs  Hoechstwerte in derselben Reihenfolge, darf fehlen ({@code null})
     * @return leer, wenn eine der beiden Seiten keinen Mittelwert hat
     */
    public static Optional<WeatherOutlook> from(List<Double> dailyMeans, List<Double> dailyMins, List<Double> dailyMaxs, int pastDays) {
        if (dailyMeans == null || dailyMeans.size() <= pastDays) return Optional.empty();
        Side recent = Side.of(dailyMeans.subList(0, pastDays));
        Side ahead = Side.of(dailyMeans.subList(pastDays, dailyMeans.size()));
        if (recent.days == 0 || ahead.days == 0) return Optional.empty();
        return Optional.of(new WeatherOutlook(recent.mean, ahead.mean, ahead.mean - recent.mean, recent.days, ahead.days,
                ahead(dailyMins, pastDays).min(Double::compare).orElse(null),
                ahead(dailyMaxs, pastDays).max(Double::compare).orElse(null)));
    }

    /** Vorhandene Werte der kommenden Tage. */
    private static java.util.stream.Stream<Double> ahead(List<Double> daily, int pastDays) {
        if (daily == null || daily.size() <= pastDays) return java.util.stream.Stream.empty();
        return daily.subList(pastDays, daily.size()).stream().filter(v -> v != null);
    }

    private record Side(double mean, int days) {
        static Side of(List<Double> values) {
            List<Double> present = values.stream().filter(v -> v != null).toList();
            if (present.isEmpty()) return new Side(0, 0);
            return new Side(present.stream().mapToDouble(Double::doubleValue).average().orElse(0), present.size());
        }
    }
}
