package com.evmonitor.application.weather;

import java.util.List;
import java.util.Optional;

/**
 * Temperatur-Ausblick am Heimladeort: Mittel der letzten Tage gegen Mittel der kommenden Tage.
 * Negative Differenz heisst, es wird kaelter. Grad Celsius, ungerundet.
 */
public record WeatherOutlook(double recentMeanC, double aheadMeanC, double deltaC, int recentDays, int aheadDays) {

    /**
     * @param dailyMeans Tagesmittel, Index 0 der aelteste Tag, die ersten {@code pastDays} liegen
     *                   in der Vergangenheit, der Rest ist Vorhersage. Luecken ({@code null})
     *                   werden uebersprungen.
     * @return leer, wenn eine der beiden Seiten keinen Wert hat
     */
    public static Optional<WeatherOutlook> from(List<Double> dailyMeans, int pastDays) {
        if (dailyMeans == null || dailyMeans.size() <= pastDays) return Optional.empty();
        Side recent = Side.of(dailyMeans.subList(0, pastDays));
        Side ahead = Side.of(dailyMeans.subList(pastDays, dailyMeans.size()));
        if (recent.days == 0 || ahead.days == 0) return Optional.empty();
        return Optional.of(new WeatherOutlook(recent.mean, ahead.mean, ahead.mean - recent.mean, recent.days, ahead.days));
    }

    private record Side(double mean, int days) {
        static Side of(List<Double> values) {
            List<Double> present = values.stream().filter(v -> v != null).toList();
            if (present.isEmpty()) return new Side(0, 0);
            return new Side(present.stream().mapToDouble(Double::doubleValue).average().orElse(0), present.size());
        }
    }
}
