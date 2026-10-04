package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteRepository;
import com.evmonitor.domain.ChargingSiteSource;
import com.evmonitor.domain.ChargingSiteUsage;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.KnownCell;
import com.evmonitor.infrastructure.security.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ladestandorte entstehen nur aus Saeulen, die das Register im Umkreis dieser Zelle fuehrt.
 * Der Client liefert Name und Zelle, der (gecachte) Registerabruf bestaetigt den Betreiber
 * und liefert Leistung und Ladepunkte - so kann niemand erfundene Standorte in die geteilte
 * Tabelle schreiben. Antwortet das Register nicht oder ist das Abfragekontingent des Nutzers
 * erschoepft, bleibt das Log ohne Standort - es geht nie verloren.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargingSiteService {

    static final int RECENT_LIMIT = 5;
    /** So viele bekannte Orte liest der Wizard - mehr als ein Nutzer je unterscheidet. */
    static final int KNOWN_LIMIT = 50;
    /** Zelle eines Standorts hat 7 Stellen (~150 m): bis hierhin "steht er dort". */
    static final double SITE_MATCH_METERS = 300;
    /** Private Zelle hat 6 Stellen (~1,2 x 0,6 km): Mittelpunkt bis Ecke sind rund 670 m. */
    static final double PRIVATE_MATCH_METERS = 800;

    private static final double EARTH_RADIUS_M = 6_371_000;

    private final ChargingSiteRepository repository;
    private final NearbyCpoService nearbyCpoService;
    private final RateLimitService rateLimitService;
    private final EvLogRepository evLogRepository;
    private final PlaceNameService placeNameService;

    public Optional<ChargingSite> resolve(UUID userId, ChargingSiteRef ref) {
        if (ref == null) return Optional.empty();
        Optional<ChargingSite> existing = repository.findByGeohashAndName(ref.geohash(), ref.name());
        if (existing.isPresent()) return existing;

        // Der Registerabruf ist ein Fremddienst - dasselbe Kontingent wie die Umkreissuche im Formular,
        // sonst liesse sich das Limit ueber gespeicherte Logs mit beliebigen Zellen umgehen.
        if (!rateLimitService.tryConsumeCpoLookup("user:" + userId)) {
            return Optional.empty();
        }
        String wanted = ChargingSite.nameKey(ref.name());
        Optional<NearbyStation> verified = nearbyCpoService.findNearbyStations(ref.geohash())
                .orElseGet(List::of).stream()
                .filter(s -> wanted.equals(ChargingSite.nameKey(s.name())))
                .findFirst();
        if (verified.isEmpty()) {
            log.info("Ladestandort '{}' bei Zelle {} nicht im Register - Log bleibt ohne Standort", ref.name(), ref.geohash());
            return Optional.empty();
        }
        return Optional.of(repository.save(fromRegister(verified.get(), ref.geohash())));
    }

    public List<ChargingSiteUsage> recentlyUsed(UUID userId) {
        return repository.findRecentlyUsedByUser(userId, RECENT_LIMIT);
    }

    /**
     * Die Orte, an denen der Nutzer schon geladen hat: jede Zelle aus den eigenen Logs, haeufigste
     * zuerst. Register-Saeulen werden in einem Zugriff angereichert, Orte ohne Saeule bekommen den
     * Ortsteil aus dem Geocoder. Mit Position stehen die Orte "hier" vorn, darunter die Saeule vor
     * der privaten Zelle. Nichts davon wird gespeichert.
     */
    public List<KnownPlace> knownPlaces(UUID userId, Position at) {
        List<KnownCell> cells = evLogRepository.findKnownCells(userId, KNOWN_LIMIT);
        List<UUID> siteIds = cells.stream().map(KnownCell::chargingSiteId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, ChargingSite> sites = siteIds.isEmpty() ? Map.of()
                : repository.findAllById(siteIds).stream().collect(Collectors.toMap(ChargingSite::id, s -> s));
        List<KnownPlace> places = new ArrayList<>();
        for (KnownCell cell : cells) {
            ChargingSite site = cell.chargingSiteId() == null ? null : sites.get(cell.chargingSiteId());
            String name = site == null ? placeNameService.nameFor(cell.geohash()).orElse(null) : null;
            KnownPlace place = new KnownPlace(cell, site, name, null, false);
            if (at != null) {
                int d = (int) Math.round(distanceMeters(new WGS84Point(at.lat(), at.lon()), cell.geohash()));
                place = place.withPosition(d, d <= (cell.isPublic() ? SITE_MATCH_METERS : PRIVATE_MATCH_METERS));
            }
            places.add(place);
        }
        places.sort(BY_RELEVANCE);
        return places;
    }

    /** Hier zuerst (Saeule vor Zelle, oeffentlich vor privat), dann nach Haeufigkeit, dann die juengste. */
    private static final Comparator<KnownPlace> BY_RELEVANCE = Comparator
            .comparing(KnownPlace::here).reversed()
            .thenComparing(p -> p.here() && p.site() != null, Comparator.reverseOrder())
            .thenComparing(p -> p.here() && p.cell().isPublic(), Comparator.reverseOrder())
            .thenComparing(p -> p.cell().usageCount(), Comparator.reverseOrder())
            .thenComparing(p -> p.cell().lastUsedAt(), Comparator.nullsLast(Comparator.reverseOrder()));

    /** Steht der Nutzer an einem Ort, an dem er schon geladen hat? Der erste Treffer "hier", sonst nichts. */
    public Optional<KnownPlace> suggest(UUID userId, double lat, double lon) {
        return knownPlaces(userId, new Position(lat, lon)).stream().filter(KnownPlace::here).findFirst();
    }

    private static double distanceMeters(WGS84Point from, String geohash) {
        if (geohash == null || geohash.isBlank()) return Double.MAX_VALUE;
        WGS84Point to;
        try {
            to = GeoHash.fromGeohashString(geohash.trim()).getBoundingBoxCenter();
        } catch (IllegalArgumentException e) {
            return Double.MAX_VALUE;
        }
        double dLat = Math.toRadians(to.getLatitude() - from.getLatitude());
        double dLon = Math.toRadians(to.getLongitude() - from.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.getLatitude())) * Math.cos(Math.toRadians(to.getLatitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static ChargingSite fromRegister(NearbyStation s, String geohash) {
        var register = new ChargingSite.RegisterDetails(s.registerId(), s.street(), s.houseNumber(),
                s.postalCode(), s.city(), s.plugTypes(), s.commissionedOn(), s.siteLabel(), s.payment(), s.openingHours());
        return new ChargingSite(UUID.randomUUID(), s.name(), s.known() ? s.name() : null, geohash,
                kw(s.maxAcKw()), kw(s.maxDcKw()), s.chargePoints(), ChargingSiteSource.REGISTER,
                register, LocalDateTime.now());
    }

    private static BigDecimal kw(Double v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }
}
