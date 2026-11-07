package com.evmonitor.application;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.GeoHash;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Die Ladestandorte einer Geohash-6-Zelle (in Deutschland etwa 0,75 × 0,6 km) für die Säulenkarte.
 *
 * <p>Anders als {@link NearbyCpoService} je Standort statt je Betreiber: EnBW steht in einer
 * Innenstadt-Zelle oft an mehreren Plätzen, jeder ist ein eigener Pin. Einrichtungen desselben
 * Betreibers im Umkreis von {@link #SITE_RADIUS_METERS} gelten als ein Standort. Ein fester
 * Abstand statt einer feineren Geohash-Zelle, weil eine Zellgrenze einen Platz sonst mitten
 * durchschneiden kann.
 *
 * <p>Der Cache liegt auf der Zelle, nie auf einer Nutzerposition; jede Zelle wird höchstens
 * einmal je 30 Tage beim Register angefragt. Er hält nur Registerdaten, keine Preise.
 * Ein Ausfall des Registers wird nicht gecacht (siehe {@link NearbyCpoService}).
 */
@Service
public class ChargingStationCellService {

    public static final String CACHE = "stationCells";

    /** Einrichtungen eines Betreibers bis zu diesem Abstand sind ein Platz (Supercharger, Parkhaus). */
    static final int SITE_RADIUS_METERS = 50;

    private static final Pattern CELL = Pattern.compile("^[0-9b-hjkmnp-z]{6}$");

    private final ChargingStationRegistryClient registry;
    private final CpoRegistryMatcher matcher;
    private final CacheManager cacheManager;

    public ChargingStationCellService(ChargingStationRegistryClient registry, CpoRegistryMatcher matcher,
                                      CacheManager cacheManager) {
        this.registry = registry;
        this.matcher = matcher;
        this.cacheManager = cacheManager;
    }

    /** Sechs Zeichen aus dem Geohash-Alphabet, klein geschrieben. */
    public static boolean isValidCell(String cell) {
        return cell != null && CELL.matcher(cell).matches();
    }

    /** Ob die Zelle schon im Cache liegt, das Register also nicht gefragt wird. Zählt für die Drossel. */
    public boolean isCached(String cell) {
        if (!isValidCell(cell)) return false;
        Cache cache = cacheManager.getCache(CACHE);
        return cache != null && cache.get(cell) != null;
    }

    /**
     * @return die Standorte der Zelle in Register-Reihenfolge, eine leere Liste wenn dort nichts
     *         steht, ein leeres Optional wenn das Register nicht antwortet oder die Zelle ungültig ist
     */
    // Spring packt das Optional vor "unless" aus: #result ist die Liste, bei Optional.empty() null.
    // Ungültige Zellen gehen am Cache vorbei, sonst scheitert schon der Schlüssel null.
    @Cacheable(value = CACHE, key = "#cell", unless = "#result == null",
            condition = "T(com.evmonitor.application.ChargingStationCellService).isValidCell(#cell)")
    public Optional<List<MapStation>> stationsInCell(String cell) {
        if (!isValidCell(cell)) {
            return Optional.empty();
        }
        BoundingBox box = GeoHash.fromGeohashString(cell).getBoundingBox();
        return registry.findStationsInBox(box.getSouthLatitude(), box.getWestLongitude(),
                        box.getNorthLatitude(), box.getEastLongitude())
                .map(this::groupBySite);
    }

    private List<MapStation> groupBySite(List<Station> stations) {
        List<Site> sites = new ArrayList<>();
        for (Station s : stations) {
            if (!s.hasPosition()) continue;
            Optional<String> canonical = matcher.match(s);
            String name = canonical.orElseGet(() -> s.brand() != null ? s.brand() : s.operator());
            sites.stream()
                    .filter(site -> site.accepts(name, s))
                    .findFirst()
                    .ifPresentOrElse(site -> site.add(s), () -> sites.add(new Site(name, canonical.isPresent(), s)));
        }
        return sites.stream()
                .map(Site::toMapStation)
                .filter(m -> RegistrySite.isChargingSite(m.known(), m.maxDcKw() != null, m.chargePoints()))
                .toList();
    }

    /** Ein Platz im Aufbau: die erste Einrichtung liefert Position und Registerdaten. */
    private static final class Site {
        private final String name;
        private final boolean known;
        private final Station first;
        private Double maxAcKw;
        private Double maxDcKw;
        private int chargePoints;
        private final LinkedHashSet<String> plugTypes = new LinkedHashSet<>();

        Site(String name, boolean known, Station first) {
            this.name = name;
            this.known = known;
            this.first = first;
            add(first);
        }

        boolean accepts(String otherName, Station s) {
            return name.equalsIgnoreCase(otherName)
                    && RegistrySite.distanceMeters(first.latitude(), first.longitude(), s.latitude(), s.longitude())
                    <= SITE_RADIUS_METERS;
        }

        void add(Station s) {
            maxAcKw = RegistrySite.max(maxAcKw, s.maxAcKw());
            maxDcKw = RegistrySite.max(maxDcKw, s.maxDcKw());
            chargePoints += s.chargePoints() == null ? 0 : s.chargePoints();
            if (s.plugTypes() != null) plugTypes.addAll(s.plugTypes());
        }

        MapStation toMapStation() {
            return new MapStation(id(), name, known, first.latitude(), first.longitude(),
                    maxAcKw, maxDcKw, chargePoints, List.copyOf(plugTypes),
                    first.street(), first.houseNumber(), first.postalCode(), first.city(),
                    first.openingHours(), first.payment());
        }

        private String id() {
            if (first.registerId() != null) return String.valueOf(first.registerId());
            String place = GeoHash.withCharacterPrecision(first.latitude(), first.longitude(), 9).toBase32();
            return "g" + place + "-" + Integer.toHexString(name.toLowerCase(java.util.Locale.ROOT).hashCode());
        }
    }
}
