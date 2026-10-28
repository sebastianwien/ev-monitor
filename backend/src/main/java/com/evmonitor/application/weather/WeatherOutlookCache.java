package com.evmonitor.application.weather;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.infrastructure.weather.TemperatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Eigene Bean, weil Spring {@code @Cacheable} ueber einen Proxy aufloest: ein Aufruf innerhalb
 * derselben Klasse ginge am Cache vorbei. Schluessel ist die Geohash-Zelle (rund 600 m), nicht
 * der Nutzer: dasselbe Wetter fuer alle, die dort laden. Ein Fehler von Open-Meteo wird nicht
 * gecacht (Optional.empty ist ein gueltiges Ergebnis, eine Exception nicht).
 */
@Component
@RequiredArgsConstructor
public class WeatherOutlookCache {

    static final int PAST_DAYS = 7;
    static final int FORECAST_DAYS = 14;

    private final TemperatureService temperatureService;

    @Cacheable(value = "weatherOutlook", key = "#geohash")
    public Optional<WeatherOutlook> forGeohash(String geohash) {
        WGS84Point center = GeoHash.fromGeohashString(geohash).getBoundingBoxCenter();
        return WeatherOutlook.from(
                temperatureService.getDailyMeans(center.getLatitude(), center.getLongitude(), PAST_DAYS, FORECAST_DAYS),
                PAST_DAYS);
    }
}
