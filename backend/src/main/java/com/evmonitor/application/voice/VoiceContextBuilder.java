package com.evmonitor.application.voice;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.ChargingSiteService;
import com.evmonitor.application.NearbyCpoService;
import com.evmonitor.application.NearbyStation;
import com.evmonitor.application.user.UserChargingProviderResponse;
import com.evmonitor.application.user.UserChargingProviderService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sammelt den Kontext fuer einen Sprachlog-Request. Die Position geht nur als Geohash (7 Stellen,
 * wie die Umkreissuche im Formular) an das Register und wird nie gespeichert.
 */
@Component
public class VoiceContextBuilder {

    static final int RECENT_LOGS = 10;
    static final int MAX_NEARBY = 10;
    private static final int GEOHASH_PRECISION = 7;
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)", Locale.GERMAN);

    private final NearbyCpoService nearbyCpoService;
    private final ChargingSiteService chargingSiteService;
    private final UserChargingProviderService providerService;
    private final EvLogRepository evLogRepository;
    private final Clock clock;

    @Autowired
    public VoiceContextBuilder(NearbyCpoService nearbyCpoService, ChargingSiteService chargingSiteService,
                               UserChargingProviderService providerService, EvLogRepository evLogRepository) {
        this(nearbyCpoService, chargingSiteService, providerService, evLogRepository, Clock.systemUTC());
    }

    VoiceContextBuilder(NearbyCpoService nearbyCpoService, ChargingSiteService chargingSiteService,
                        UserChargingProviderService providerService, EvLogRepository evLogRepository, Clock clock) {
        this.nearbyCpoService = nearbyCpoService;
        this.chargingSiteService = chargingSiteService;
        this.providerService = providerService;
        this.evLogRepository = evLogRepository;
        this.clock = clock;
    }

    public VoiceContext build(UUID userId, Car car, Double lat, Double lon, ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        List<EvLog> recentLogs = evLogRepository.findLatestByCarId(car.getId(), RECENT_LOGS);
        Integer lastOdometer = recentLogs.stream().map(EvLog::getOdometerKm).filter(Objects::nonNull)
                .max(Integer::compare).orElse(null);
        List<String> operators = recentLogs.stream().map(EvLog::getCpoName).filter(Objects::nonNull).distinct().toList();
        BigDecimal capacity = car.getEffectiveBatteryCapacityKwh();
        String vehicle = capacity == null ? null
                : "Nutzbare Akkukapazität " + capacity.stripTrailingZeros().toPlainString() + " kWh";

        return new VoiceContext(candidates(userId, lat, lon), activeTariffs(userId, now.toLocalDate()), operators,
                lastOdometer, now.format(TODAY) + ", Zeitzone " + zone.getId(), vehicle);
    }

    private List<PlaceCandidate> candidates(UUID userId, Double lat, Double lon) {
        List<PlaceCandidate> out = new ArrayList<>();
        out.add(new PlaceCandidate.Home());
        Set<String> known = new HashSet<>();
        chargingSiteService.recentlyUsed(userId).forEach(u -> {
            out.add(new PlaceCandidate.Site(u));
            known.add(key(u.site().name(), u.site().geohash()));
        });
        nearby(lat, lon).stream()
                .filter(s -> !known.contains(key(s.name(), s.geohash())))
                .limit(MAX_NEARBY)
                .forEach(s -> out.add(new PlaceCandidate.Station(s)));
        return out;
    }

    private List<NearbyStation> nearby(Double lat, Double lon) {
        if (lat == null || lon == null) return List.of();
        String geohash = GeoHash.withCharacterPrecision(lat, lon, GEOHASH_PRECISION).toBase32();
        List<NearbyStation> close = nearbyCpoService.findNearbyStations(geohash).orElseGet(List::of);
        if (!close.isEmpty()) return close;
        return nearbyCpoService.findNearbyStations(geohash, NearbyCpoService.MAX_RADIUS_METERS).orElseGet(List::of);
    }

    private List<UserChargingProviderResponse> activeTariffs(UUID userId, LocalDate today) {
        return providerService.getAll(userId).stream()
                .filter(t -> t.activeUntil() == null || !t.activeUntil().isBefore(today))
                .toList();
    }

    private static String key(String name, String geohash) {
        return ChargingSite.nameKey(name) + "@" + geohash;
    }
}
