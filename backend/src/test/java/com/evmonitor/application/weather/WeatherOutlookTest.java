package com.evmonitor.application.weather;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reine Rechnung: Mittel der letzten Tage gegen Mittel der kommenden Tage. */
class WeatherOutlookTest {

    @Test
    void mittelDerLetztenTageGegenMittelDerKommenden() {
        // 3 Tage Vergangenheit (12, 10, 8), 4 Tage Vorhersage (6, 4, 2, 0)
        List<Double> daily = List.of(12.0, 10.0, 8.0, 6.0, 4.0, 2.0, 0.0);

        WeatherOutlook o = WeatherOutlook.from(daily, 3).orElseThrow();

        assertThat(o.recentMeanC()).isEqualTo(10.0);
        assertThat(o.aheadMeanC()).isEqualTo(3.0);
        assertThat(o.deltaC()).isEqualTo(-7.0);
        assertThat(o.recentDays()).isEqualTo(3);
        assertThat(o.aheadDays()).isEqualTo(4);
    }

    @Test
    void luckenWerdenUebersprungenUndNichtAlsNullGezaehlt() {
        List<Double> daily = Arrays.asList(10.0, null, 8.0, null, 4.0);

        WeatherOutlook o = WeatherOutlook.from(daily, 3).orElseThrow();

        assertThat(o.recentMeanC()).isEqualTo(9.0);
        assertThat(o.aheadMeanC()).isEqualTo(4.0);
        assertThat(o.recentDays()).isEqualTo(2);
        assertThat(o.aheadDays()).isEqualTo(1);
    }

    @Test
    void ohneVergangenheitOderOhneVorhersageKeinAusblick() {
        assertThat(WeatherOutlook.from(List.of(10.0, 9.0), 3)).isEmpty();
        assertThat(WeatherOutlook.from(Arrays.asList(null, null, null, 5.0), 3)).isEmpty();
        assertThat(WeatherOutlook.from(List.of(), 3)).isEmpty();
    }
}
