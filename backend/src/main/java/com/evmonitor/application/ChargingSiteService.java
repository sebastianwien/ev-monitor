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
    /** Hoechstens so viele bekannte Orte je Antwort - mehr unterscheidet niemand auf einen Blick. */
    static final int KNOWN_LIMIT = 3;
    static final int PUBLIC_PRECISION = 7;
    static final int PRIVATE_PRECISION = 6;

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
     * Mit Position: die Orte, an denen der Nutzer genau hier schon geladen hat - seine Logs in der
     * 7-stelligen Zelle der Position (oeffentlich, ~150 m) und in der 6-stelligen (privat, ~600 m),
     * haeufigste zuerst, Saeule vor Zelle. Ohne Position: seine haeufigsten Orte, mit Ortsteil vom
     * Geocoder fuer Orte ohne Saeule. Nichts davon wird gespeichert.
     */
    public List<KnownPlace> knownPlaces(UUID userId, Position at) {
        List<KnownCell> cells = at == null
                ? evLogRepository.findKnownCells(userId, KNOWN_LIMIT)
                : evLogRepository.findKnownCellsIn(userId, cellsAt(at), KNOWN_LIMIT);
        List<UUID> siteIds = cells.stream().map(KnownCell::chargingSiteId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, ChargingSite> sites = siteIds.isEmpty() ? Map.of()
                : repository.findAllById(siteIds).stream().collect(Collectors.toMap(ChargingSite::id, s -> s));
        List<KnownPlace> places = new ArrayList<>();
        for (KnownCell cell : cells) {
            ChargingSite site = cell.chargingSiteId() == null ? null : sites.get(cell.chargingSiteId());
            String name = site == null && at == null ? placeNameService.nameFor(cell.geohash()).orElse(null) : null;
            places.add(new KnownPlace(cell, site, name, at != null));
        }
        if (at != null) places.sort(Comparator.comparing((KnownPlace p) -> p.site() == null));
        return places;
    }

    /** Die Zellen "hier": 7 Stellen fuer oeffentliche Logs, 6 fuer private. */
    static List<String> cellsAt(Position at) {
        String cell7 = GeoHash.withCharacterPrecision(at.lat(), at.lon(), PUBLIC_PRECISION).toBase32();
        return List.of(cell7, cell7.substring(0, PRIVATE_PRECISION));
    }

    /** Steht der Nutzer an einem Ort, an dem er schon geladen hat? Der haeufigste Treffer, Saeule zuerst. */
    public Optional<KnownPlace> suggest(UUID userId, double lat, double lon) {
        return knownPlaces(userId, new Position(lat, lon)).stream().findFirst();
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
