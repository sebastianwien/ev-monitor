package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Textsuche im Ladesaeulenregister fuer die Ortswahl im Log-Formular: der Nutzer tippt
 * "EnBW Lichtenau" oder "Fuchsgraben Lichtenau" und bekommt die Saeule als Kachel.
 *
 * <p>Gruppiert nach Betreiber und Zelle (7 Stellen): derselbe Betreiber in zwei Orten sind
 * zwei Kacheln, zwei Saeulen am selben Standort eine. Register-Reihenfolge, hoechstens
 * {@link NearbyCpoService#MAX_STATIONS}.
 */
@Service
public class StationSearchService {

    private static final int SITE_PRECISION = 7;

    private final ChargingStationRegistryClient registry;
    private final CpoRegistryMatcher matcher;

    public StationSearchService(ChargingStationRegistryClient registry, CpoRegistryMatcher matcher) {
        this.registry = registry;
        this.matcher = matcher;
    }

    /**
     * @param query normalisierter Suchtext (getrimmt, klein, einfache Leerzeichen) - der Cache-Schluessel
     * @return die Treffer, oder ein leeres Optional wenn das Register nicht antwortet
     */
    // Spring packt das Optional vor "unless" aus: #result ist die Liste, bei Optional.empty() null.
    @Cacheable(value = "stationSearch", key = "#query", unless = "#result == null")
    public Optional<List<StationMatch>> search(String query) {
        return registry.searchStations(query).map(this::groupBySite);
    }

    private List<StationMatch> groupBySite(List<Station> stations) {
        Map<String, StationMatch> bySite = new LinkedHashMap<>();
        for (Station s : stations) {
            if (!s.hasPosition()) continue;
            Optional<String> canonical = matcher.match(s);
            String name = canonical.orElseGet(() -> s.brand() != null ? s.brand() : s.operator());
            String geohash = GeoHash.withCharacterPrecision(s.latitude(), s.longitude(), SITE_PRECISION).toBase32();
            bySite.merge(name.toLowerCase() + "@" + geohash, toMatch(s, name, canonical.isPresent(), geohash),
                    StationSearchService::merge);
        }
        return bySite.values().stream()
                .filter(StationMatch::isChargingSite)
                .limit(NearbyCpoService.MAX_STATIONS)
                .toList();
    }

    private static StationMatch toMatch(Station s, String name, boolean known, String geohash) {
        return new StationMatch(name, known, s.maxAcKw(), s.maxDcKw(),
                s.chargePoints() == null ? 0 : s.chargePoints(), geohash, s.registerId(),
                s.street(), s.houseNumber(), s.postalCode(), s.city(), s.plugTypes());
    }

    /** Leistung je Ladeart als Maximum, Ladepunkte als Summe, Registerdaten der ersten Saeule. */
    private static StationMatch merge(StationMatch a, StationMatch b) {
        return new StationMatch(a.name(), a.known(), max(a.maxAcKw(), b.maxAcKw()), max(a.maxDcKw(), b.maxDcKw()),
                a.chargePoints() + b.chargePoints(), a.geohash(), a.registerId(),
                a.street(), a.houseNumber(), a.postalCode(), a.city(), a.plugTypes());
    }

    private static Double max(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }
}
