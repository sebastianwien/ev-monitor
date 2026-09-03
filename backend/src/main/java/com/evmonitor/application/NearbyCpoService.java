package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Schlaegt die Ladenetze vor, die an einem Standort tatsaechlich stehen.
 *
 * <p>Fragt dazu das Ladesaeulenregister im Umkreis ab und behaelt nur, was sich einem
 * bekannten Ladenetz zuordnen laesst. Eine leere Liste ist ein gueltiges Ergebnis und wird
 * mitgecacht - in laendlicher Lage ist sie der Normalfall. Ein leeres {@link Optional}
 * heisst dagegen, dass das Register nicht geantwortet hat; das wird bewusst nicht gecacht,
 * sonst wuerde ein Ausfall von wenigen Minuten dreissig Tage lang nachwirken.
 *
 * <p>Der Cache liegt auf der Geohash-Zelle, nicht auf der Nutzerposition. Damit wird jede
 * Zelle hoechstens einmal je Cache-Dauer beim fremden Dienst angefragt, unabhaengig davon,
 * wie viele Nutzer dort laden. Die Dauer von 30 Tagen deckt den monatlichen
 * Aktualisierungsrhythmus des Registers ab (siehe {@code CacheCustomizationConfig}).
 */
@Service
@Slf4j
public class NearbyCpoService {

    private final ChargingStationRegistryClient registry;
    private final CpoRegistryMatcher matcher;
    private final int radiusMeters;

    public NearbyCpoService(ChargingStationRegistryClient registry,
                            CpoRegistryMatcher matcher,
                            @Value("${charging-station-registry.radius-meters:250}") int radiusMeters) {
        this.registry = registry;
        this.matcher = matcher;
        this.radiusMeters = radiusMeters;
    }

    /**
     * Die Ladenetze im Umkreis der Geohash-Zelle, das haeufigste zuerst.
     *
     * @param geohash Zelle der Ladung; fuer oeffentliches Laden sieben Stellen (~150 m)
     * @return die Vorschlaege, oder ein leeres Optional wenn das Register nicht antwortet
     */
    // Spring packt das Optional aus, bevor es "unless" auswertet: #result ist hier die Liste
    // selbst, bei Optional.empty() null. Deshalb der Null-Vergleich und nicht isPresent().
    @Cacheable(value = "nearbyCpos", key = "#geohash", unless = "#result == null")
    public Optional<List<String>> findNearbyCpos(String geohash) {
        WGS84Point center = centerOf(geohash);
        if (center == null) {
            return Optional.empty();
        }

        return registry.findStationsNearby(center.getLatitude(), center.getLongitude(), radiusMeters)
                .map(matcher::matchAll);
    }

    private WGS84Point centerOf(String geohash) {
        if (geohash == null || geohash.isBlank()) {
            return null;
        }
        try {
            return GeoHash.fromGeohashString(geohash.trim()).getBoundingBoxCenter();
        } catch (Exception e) {
            log.debug("Ungueltiger Geohash fuer CPO-Vorschlaege: {}", e.getMessage());
            return null;
        }
    }
}
