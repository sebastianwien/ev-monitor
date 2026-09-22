package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Oeffentliches Teilen einer Fahrzeugseite (Verbrauch, Ladekosten, Ladeliste).
 *
 * Opt-in pro Fahrzeug: geteilt wird genau ein Auto, nie ein Konto. Der Token
 * entsteht erst beim Teilen und ist mit einem Aufruf wieder weg; ein erneutes
 * Teilen vergibt einen neuen, der alte Link bleibt tot.
 *
 * Alle Kennzahlen kommen aus {@link EvLogStatisticsService} - hier wird nichts
 * selbst gerechnet.
 */
@Service
public class CarShareService {

    private static final Logger log = LoggerFactory.getLogger(CarShareService.class);

    /** Pfad der oeffentlichen Seite - muss zur Vue-Route passen. */
    static final String SHARE_PATH = "/fahrzeug/";

    private static final String TOKEN_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TOKEN_LENGTH = 12;
    private static final int MONTHS_SHOWN = 12;
    private static final int RECENT_CHARGES = 20;

    private final CarRepository carRepository;
    private final EvLogStatisticsService statisticsService;
    private final EvLogService evLogService;
    private final SecureRandom random = new SecureRandom();
    private final String baseUrl;

    public CarShareService(CarRepository carRepository,
                           EvLogStatisticsService statisticsService,
                           EvLogService evLogService,
                           @Value("${app.base-url}") String baseUrl) {
        this.carRepository = carRepository;
        this.statisticsService = statisticsService;
        this.evLogService = evLogService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Gibt das Fahrzeug oeffentlich frei und liefert die URL. Idempotent.
     *
     * @throws IllegalArgumentException Auto unbekannt oder gehoert einem anderen Nutzer (-> 404)
     */
    @Transactional
    public ShareResponse createShare(UUID carId, User user) {
        requireOwnership(carId, user);
        String existing = carRepository.findShareToken(carId).orElse(null);
        if (existing != null && !existing.isBlank()) {
            return toResponse(existing);
        }
        String token = generateToken();
        carRepository.setShareToken(carId, token, LocalDateTime.now());
        log.info("Fahrzeug geteilt: car={} user={}", carId, user.getId());
        return toResponse(token);
    }

    /** Macht die oeffentliche URL ungueltig. Idempotent. */
    @Transactional
    public void revokeShare(UUID carId, User user) {
        requireOwnership(carId, user);
        carRepository.clearShareToken(carId);
        log.info("Fahrzeug nicht mehr geteilt: car={} user={}", carId, user.getId());
    }

    @Transactional(readOnly = true)
    public Optional<ShareResponse> findShare(UUID carId, User user) {
        requireOwnership(carId, user);
        return carRepository.findShareToken(carId).filter(t -> !t.isBlank()).map(this::toResponse);
    }

    /**
     * Oeffentlicher Lookup - ohne Authentifizierung. Leeres Optional fuer unbekannte
     * und widerrufene Token gleichermassen.
     */
    @Transactional(readOnly = true)
    public Optional<PublicCarResponse> getPublicCar(String token) {
        return findSharedCar(token).map(this::toPublic);
    }

    /** Das Auto hinter dem Token, nur wenn der Besitzer sein Foto oeffentlich gestellt hat. */
    @Transactional(readOnly = true)
    public Optional<Car> findSharedCarWithPublicImage(String token) {
        return findSharedCar(token).filter(c -> c.isImagePublic() && c.getImagePath() != null);
    }

    // ── intern ───────────────────────────────────────────────────────────────

    private Optional<Car> findSharedCar(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return carRepository.findByShareToken(token);
    }

    private PublicCarResponse toPublic(Car car) {
        UUID owner = car.getUserId();
        EvLogStatisticsResponse stats = statisticsService.getStatistics(car.getId(), owner, null, null, "MONTH");

        List<PublicCarResponse.MonthPoint> months = stats.chargesOverTime() == null ? List.of()
                : stats.chargesOverTime().stream()
                        .skip(Math.max(0, stats.chargesOverTime().size() - MONTHS_SHOWN))
                        .map(p -> new PublicCarResponse.MonthPoint(
                                p.timestamp().toLocalDate().withDayOfMonth(1),
                                p.kwhCharged(), p.costEur(), p.consumptionKwhPer100km()))
                        .toList();

        List<PublicCarResponse.Charge> charges = evLogService.getLogsForCar(car.getId(), owner, RECENT_CHARGES).stream()
                .map(l -> new PublicCarResponse.Charge(
                        l.loggedAt().toLocalDate(),
                        l.kwhCharged(),
                        l.costEur(),
                        l.chargeDurationMinutes(),
                        l.chargingType() != null ? l.chargingType().name() : null,
                        l.maxChargingPowerKw(),
                        Boolean.TRUE.equals(l.consumptionImplausible()) ? null : l.consumptionKwhPer100km(),
                        l.isPublicCharging()))
                .toList();

        return new PublicCarResponse(
                car.getModel() != null
                        ? car.getModel().getBrand().getDisplayString() + " " + car.getModel().getDisplayName()
                        : null,
                car.getYear(),
                car.isImagePublic() && car.getImagePath() != null,
                stats.totalCharges(),
                stats.totalKwhCharged(),
                stats.totalDistanceKm(),
                stats.avgConsumptionKwhPer100km(),
                stats.avgCostPerKwh(),
                costPer100km(stats.energyCostEur(), stats.totalDistanceKm()),
                publicShare(stats.locationSplit()),
                stats.summerConsumptionKwhPer100km(),
                stats.winterConsumptionKwhPer100km(),
                peerComparison(stats.peerBenchmark()),
                months,
                charges);
    }

    private static PublicCarResponse.PeerComparison peerComparison(EvLogStatisticsResponse.PeerBenchmark b) {
        if (b == null || b.peerAvgConsumptionKwhPer100km() == null || b.uniquePeerUsers() <= 0) return null;
        return new PublicCarResponse.PeerComparison(
                b.peerAvgConsumptionKwhPer100km(), b.uniquePeerUsers(),
                b.matchType() != null ? b.matchType().name() : null);
    }

    private static BigDecimal costPer100km(BigDecimal energyCost, BigDecimal distanceKm) {
        if (energyCost == null || distanceKm == null || distanceKm.signum() <= 0) return null;
        return energyCost.multiply(BigDecimal.valueOf(100)).divide(distanceKm, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal publicShare(EvLogStatisticsResponse.LocationSplit split) {
        if (split == null) return null;
        BigDecimal pub = nz(split.publicKwh());
        BigDecimal known = pub.add(nz(split.privateKwh()));
        if (known.signum() <= 0) return null;
        return pub.multiply(BigDecimal.valueOf(100)).divide(known, 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void requireOwnership(UUID carId, User user) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));
        if (!car.isOwnedBy(user.getId())) {
            throw new IllegalArgumentException("Car not found for current user (ownership mismatch).");
        }
    }

    private ShareResponse toResponse(String token) {
        return new ShareResponse(token, baseUrl + SHARE_PATH + token);
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_ALPHABET.charAt(random.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }
}
