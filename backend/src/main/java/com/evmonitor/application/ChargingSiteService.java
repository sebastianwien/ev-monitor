package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteRepository;
import com.evmonitor.domain.ChargingSiteSource;
import com.evmonitor.domain.ChargingSiteUsage;
import com.evmonitor.infrastructure.security.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    /** Mehr Ladeorte hat kaum jemand; reicht für "hat hier schon geladen" bei der Zuordnung. */
    static final int USED_SITES_LIMIT = 200;

    private final ChargingSiteRepository repository;
    private final NearbyCpoService nearbyCpoService;
    private final RateLimitService rateLimitService;

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

    /** Register-Säulen um die Zelle (gecacht); leer, wenn das Register nicht antwortet. */
    public Optional<List<NearbyStation>> stationsAround(String geohash) {
        return nearbyCpoService.findNearbyStations(geohash);
    }

    /** Ob {@link #stationsAround} ohne Registerabruf auskommt. */
    public boolean isRegisterCached(String geohash) {
        return nearbyCpoService.isStationsCached(geohash, null);
    }

    /**
     * Der Standort zu einer Säule, die das Register schon geliefert hat - für die automatische
     * Zuordnung, die kein Nutzer ausgelöst hat und deshalb kein Nutzerkontingent verbraucht.
     * Legen zwei Zuordnungen denselben Standort gleichzeitig an, scheitert die zweite am Unique
     * auf Zelle und Name; ihre Ladung bleibt dann ohne Standort.
     */
    public ChargingSite siteFor(NearbyStation station) {
        return repository.findByGeohashAndName(station.geohash(), station.name())
                .orElseGet(() -> repository.save(fromRegister(station, station.geohash())));
    }

    public List<ChargingSiteUsage> recentlyUsed(UUID userId) {
        return repository.findRecentlyUsedByUser(userId, RECENT_LIMIT);
    }

    /** {@link ChargingSiteMatcher#siteKey} aller Standorte, an denen der Nutzer schon geladen hat. */    public Set<String> usedSiteKeys(UUID userId) {
        return repository.findRecentlyUsedByUser(userId, USED_SITES_LIMIT).stream()
                .map(u -> ChargingSiteMatcher.siteKey(u.site().geohash(), u.site().name()))
                .collect(Collectors.toSet());
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
