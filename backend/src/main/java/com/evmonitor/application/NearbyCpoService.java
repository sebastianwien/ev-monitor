package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Mehr Kacheln passen nicht auf einen Handy-Screen, und mehr Auswahl hilft dort nicht. */
    static final int MAX_STATIONS = 5;

    /** Vier Nachkommastellen (~11 m): Saeulen desselben Betreibers dichter beieinander sind ein Standort. */
    private static final double SITE_GRID = 1e4;

    private static final double EARTH_RADIUS_M = 6_371_000;

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

    /**
     * Die Ladestandorte im Umkreis der Geohash-Zelle, der naechste zuerst, hoechstens
     * {@link #MAX_STATIONS}. Anders als {@link #findNearbyCpos} bleiben unbekannte Betreiber
     * mit Rohnamen erhalten - im Formular soll die Saeule auftauchen, an der der Nutzer steht.
     * Einrichtungen ohne Position im Register lassen sich nicht einordnen und fallen weg.
     *
     * @return die Vorschlaege, oder ein leeres Optional wenn das Register nicht antwortet
     */
    @Cacheable(value = "nearbyStations", key = "#geohash", unless = "#result == null")
    public Optional<List<NearbyStation>> findNearbyStations(String geohash) {
        WGS84Point center = centerOf(geohash);
        if (center == null) {
            return Optional.empty();
        }
        return registry.findStationsNearby(center.getLatitude(), center.getLongitude(), radiusMeters)
                .map(stations -> groupBySite(stations, center));
    }

    private List<NearbyStation> groupBySite(List<Station> stations, WGS84Point center) {
        Map<String, NearbyStation> sites = new LinkedHashMap<>();
        for (Station s : stations) {
            if (!s.hasPosition()) continue;
            Optional<String> canonical = matcher.match(s);
            String name = canonical.orElseGet(() -> s.brand() != null ? s.brand() : s.operator());
            String key = name.toLowerCase() + "@" + Math.round(s.latitude() * SITE_GRID)
                    + "," + Math.round(s.longitude() * SITE_GRID);
            sites.merge(key, toNearby(s, name, canonical.isPresent(), center), NearbyCpoService::mergeSite);
        }
        return sites.values().stream()
                .sorted(Comparator.comparingInt(NearbyStation::distanceMeters))
                .limit(MAX_STATIONS)
                .toList();
    }

    private static NearbyStation toNearby(Station s, String name, boolean known, WGS84Point center) {
        int distance = (int) Math.round(distanceMeters(center, s.latitude(), s.longitude()));
        return new NearbyStation(name, known, distance, s.powerKw(), s.fastCharging(),
                s.chargePoints() == null ? 0 : s.chargePoints());
    }

    private static NearbyStation mergeSite(NearbyStation a, NearbyStation b) {
        Double power = a.maxPowerKw() == null ? b.maxPowerKw()
                : b.maxPowerKw() == null ? a.maxPowerKw() : Math.max(a.maxPowerKw(), b.maxPowerKw());
        return new NearbyStation(a.name(), a.known(), Math.min(a.distanceMeters(), b.distanceMeters()),
                power, a.fastCharging() || b.fastCharging(), a.chargePoints() + b.chargePoints());
    }

    /** Haversine - im Umkreis weniger hundert Meter mehr als genau genug. */
    private static double distanceMeters(WGS84Point from, double lat, double lon) {
        double dLat = Math.toRadians(lat - from.getLatitude());
        double dLon = Math.toRadians(lon - from.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.getLatitude())) * Math.cos(Math.toRadians(lat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
