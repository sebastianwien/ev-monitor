package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteRepository;
import com.evmonitor.domain.ChargingSiteSource;
import com.evmonitor.domain.ChargingSiteUsage;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.PrivateCellCount;
import com.evmonitor.infrastructure.security.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    /** So viele Standorte werden fuer den Treffer verglichen - mehr als der Nutzer je anfaehrt. */
    static final int SUGGESTION_CANDIDATES = 30;
    /** Zelle eines Standorts hat 7 Stellen (~150 m): bis hierhin "steht er dort". */
    static final double SITE_MATCH_METERS = 300;
    /** Private Zelle hat 6 Stellen (~1,2 x 0,6 km): Mittelpunkt bis Ecke sind rund 670 m. */
    static final double PRIVATE_MATCH_METERS = 800;
    /** Ab wie vielen privaten Ladungen in einer Zelle sie als "sein Anschluss" gilt ... */
    static final int PRIVATE_MIN_LOGS = 5;
    /** ... und welchen Anteil an allen privaten Ladungen sie dafuer haben muss (Annahme, nicht gemessen). */
    static final double PRIVATE_MIN_SHARE = 0.6;

    private static final double EARTH_RADIUS_M = 6_371_000;

    private final ChargingSiteRepository repository;
    private final NearbyCpoService nearbyCpoService;
    private final RateLimitService rateLimitService;
    private final EvLogRepository evLogRepository;

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
     * Steht der Nutzer an einem Ort, an dem er schon geladen hat? Ein bekannter oeffentlicher
     * Standort gewinnt vor der privaten Zelle, weil er die praezisere Aussage ist. Nichts davon
     * wird gespeichert, es sind nur die eigenen Logs. Nutzer ohne Logs mit Zelle bekommen nie
     * einen Vorschlag - sie waehlen wie bisher aus der Liste.
     */
    public Optional<ChargingSuggestion> suggest(UUID userId, double lat, double lon) {
        WGS84Point here = new WGS84Point(lat, lon);
        Optional<ChargingSiteUsage> site = repository.findRecentlyUsedByUser(userId, SUGGESTION_CANDIDATES).stream()
                .filter(u -> distanceMeters(here, u.site().geohash()) <= SITE_MATCH_METERS)
                .findFirst();
        if (site.isPresent()) {
            UUID provider = evLogRepository.findMostRecentChargingProviderAtGeohash(userId, site.get().site().geohash(), true)
                    .orElse(null);
            return Optional.of(ChargingSuggestion.site(site.get(), provider));
        }
        return privateCell(evLogRepository.countPrivateLogsByCell(userId))
                .filter(cell -> distanceMeters(here, cell) <= PRIVATE_MATCH_METERS)
                .map(cell -> ChargingSuggestion.privateCell());
    }

    /** Die dominante private Zelle: genug Ladungen und klar die haeufigste, sonst keine. */
    static Optional<String> privateCell(List<PrivateCellCount> counts) {
        if (counts.isEmpty()) return Optional.empty();
        long total = counts.stream().mapToLong(PrivateCellCount::count).sum();
        PrivateCellCount top = counts.stream().max(java.util.Comparator.comparingLong(PrivateCellCount::count)).orElseThrow();
        if (top.count() < PRIVATE_MIN_LOGS || (double) top.count() / total < PRIVATE_MIN_SHARE) return Optional.empty();
        return Optional.of(top.cell());
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
