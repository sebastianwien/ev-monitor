package com.evmonitor.application.weather;

import com.evmonitor.infrastructure.weather.TemperatureService.DailyWeather;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reine Rechnung: Mittel der letzten Tage gegen Mittel der kommenden Tage, dazu die Tage der Vorhersage. */
class WeatherOutlookTest {

    /** Nur Tagesmittel, alles andere fehlt. */
    private static DailyWeather means(List<Double> means) {
        return new DailyWeather(null, means, null, null, null, null);
    }

    @Test
    void mittelDerLetztenTageGegenMittelDerKommenden() {
        // 3 Tage Vergangenheit (12, 10, 8), 4 Tage Vorhersage (6, 4, 2, 0)
        WeatherOutlook o = WeatherOutlook.from(means(List.of(12.0, 10.0, 8.0, 6.0, 4.0, 2.0, 0.0)), 3).orElseThrow();

        assertThat(o.recentMeanC()).isEqualTo(10.0);
        assertThat(o.aheadMeanC()).isEqualTo(3.0);
        assertThat(o.deltaC()).isEqualTo(-7.0);
        assertThat(o.recentDays()).isEqualTo(3);
        assertThat(o.aheadDays()).isEqualTo(4);
    }

    @Test
    void luckenWerdenUebersprungenUndNichtAlsNullGezaehlt() {
        WeatherOutlook o = WeatherOutlook.from(means(Arrays.asList(10.0, null, 8.0, null, 4.0)), 3).orElseThrow();

        assertThat(o.recentMeanC()).isEqualTo(9.0);
        assertThat(o.aheadMeanC()).isEqualTo(4.0);
        assertThat(o.recentDays()).isEqualTo(2);
        assertThat(o.aheadDays()).isEqualTo(1);
    }

    @Test
    void ohneVergangenheitOderOhneVorhersageKeinAusblick() {
        assertThat(WeatherOutlook.from(means(List.of(10.0, 9.0)), 3)).isEmpty();
        assertThat(WeatherOutlook.from(means(Arrays.asList(null, null, null, 5.0)), 3)).isEmpty();
        assertThat(WeatherOutlook.from(means(List.of()), 3)).isEmpty();
        assertThat(WeatherOutlook.from(means(null), 3)).isEmpty();
    }

    @Test
    void tageNurAusDerVorhersageUndNurVollstaendige() {
        // 1 Tag Vergangenheit, 3 Tage Vorhersage; dem 03.11. fehlt der Tiefstwert, die Boe darf fehlen
        DailyWeather d = new DailyWeather(
                List.of("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04"),
                List.of(5.0, 3.0, 2.0, 4.0),
                Arrays.asList(-8.0, -1.0, null, 0.5),
                Arrays.asList(20.0, 6.0, 4.0, 7.2),
                Arrays.asList(0, 3, 61, 71),
                Arrays.asList(10.0, 45.0, 20.0, null));

        WeatherOutlook o = WeatherOutlook.from(d, 1).orElseThrow();

        assertThat(o.days()).containsExactly(
                new WeatherOutlook.Day("2026-11-02", -1.0, 6.0, 3, 45.0),
                new WeatherOutlook.Day("2026-11-04", 0.5, 7.2, 71, null));
    }

    @Test
    void ohneWettercodeKeineTageAberDerAusblickBleibt() {
        WeatherOutlook o = WeatherOutlook.from(new DailyWeather(List.of("2026-11-01", "2026-11-02"), List.of(5.0, 3.0),
                List.of(1.0, 0.0), List.of(9.0, 8.0), null, null), 1).orElseThrow();

        assertThat(o.deltaC()).isEqualTo(-2.0);
        assertThat(o.days()).isEmpty();
    }
}
