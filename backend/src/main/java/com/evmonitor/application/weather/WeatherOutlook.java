package com.evmonitor.application.weather;

import com.evmonitor.infrastructure.weather.TemperatureService.DailyWeather;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Temperatur-Ausblick am Heimladeort: Mittel der letzten Tage gegen Mittel der kommenden Tage.
 * Negative Differenz heisst, es wird kaelter. Dazu jeder Tag der Vorhersage mit Wetterlage.
 * Grad Celsius, ungerundet.
 */
public record WeatherOutlook(double recentMeanC, double aheadMeanC, double deltaC, int recentDays, int aheadDays,
                             List<Day> days) {

    /**
     * Ein Tag der Vorhersage.
     *
     * @param date    Datum in Ortszeit (ISO)
     * @param code    WMO-Wettercode
     * @param gustKmh staerkste Boe in km/h, {@code null} ohne Wert
     */
    public record Day(String date, double minC, double maxC, int code, Double gustKmh) {}

    /**
     * @param daily    Tageswerte, die ersten {@code pastDays} liegen in der Vergangenheit, der Rest ist
     *                 Vorhersage. Luecken ({@code null}) werden uebersprungen.
     * @return leer, wenn eine der beiden Seiten keinen Mittelwert hat
     */
    public static Optional<WeatherOutlook> from(DailyWeather daily, int pastDays) {
        List<Double> means = daily.means();
        if (means == null || means.size() <= pastDays) return Optional.empty();
        Side recent = Side.of(means.subList(0, pastDays));
        Side ahead = Side.of(means.subList(pastDays, means.size()));
        if (recent.days == 0 || ahead.days == 0) return Optional.empty();
        return Optional.of(new WeatherOutlook(recent.mean, ahead.mean, ahead.mean - recent.mean, recent.days, ahead.days,
                aheadDays(daily, pastDays)));
    }

    /** Tage der Vorhersage mit Datum, Tiefst-, Hoechstwert und Wettercode; ein Tag mit Luecke faellt weg. */
    private static List<Day> aheadDays(DailyWeather d, int pastDays) {
        List<Day> days = new ArrayList<>();
        int size = d.dates() == null ? 0 : d.dates().size();
        for (int i = pastDays; i < size; i++) {
            String date = at(d.dates(), i);
            Double min = at(d.mins(), i);
            Double max = at(d.maxs(), i);
            Integer code = at(d.codes(), i);
            if (date == null || min == null || max == null || code == null) continue;
            days.add(new Day(date, min, max, code, at(d.gusts(), i)));
        }
        return List.copyOf(days);
    }

    private static <T> T at(List<T> values, int i) {
        return values != null && i < values.size() ? values.get(i) : null;
    }

    private record Side(double mean, int days) {
        static Side of(List<Double> values) {
            List<Double> present = values.stream().filter(v -> v != null).toList();
            if (present.isEmpty()) return new Side(0, 0);
            return new Side(present.stream().mapToDouble(Double::doubleValue).average().orElse(0), present.size());
        }
    }
}
