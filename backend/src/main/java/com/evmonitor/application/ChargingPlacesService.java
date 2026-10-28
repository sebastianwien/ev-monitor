package com.evmonitor.application;

import com.evmonitor.application.ChargingPlaceResponse.Kind;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.infrastructure.persistence.ChargingSavingsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ladeorte eines Autos für die Story-Karte "Wo du lädst": je gespeichertem Geohash Menge, Geld und
 * Preis je kWh. Der Ort wird nicht neu gerundet, er ist so grob wie gespeichert.
 *
 * <p>Es zählen dieselben Ladungen wie in {@link EvLogStatisticsService#getStatistics}: nicht gelöscht,
 * in der Statistik (Seed-Nutzer: alle), Ladetag im Zeitraum. So ergibt die Summe der Orte eines Monats
 * die Stromkosten der Kosten-Kachel, abzüglich der Ladungen ohne Ort.
 */
@Service
@RequiredArgsConstructor
public class ChargingPlacesService {

    private static final Comparator<ChargingPlaceResponse> MOST_MONEY_FIRST =
            Comparator.comparing(ChargingPlaceResponse::costEur).reversed()
                    .thenComparing(ChargingPlaceResponse::charges, Comparator.reverseOrder())
                    .thenComparing(ChargingPlaceResponse::geohash);

    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final EvLogRepository evLogRepository;
    private final ChargingSavingsQueryRepository savingsQueryRepository;

    /** Orte nach Geld absteigend. from/to sind inklusiv, null = offen. Fremdes Auto = 404. */
    @Transactional(readOnly = true)
    public List<ChargingPlaceResponse> getPlaces(UUID carId, UUID userId, LocalDate from, LocalDate to) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> NotFoundException.forEntity("Car", carId));
        if (!car.isOwnedBy(userId)) {
            throw NotFoundException.forEntity("Car", carId);
        }
        boolean isSeedUser = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.forEntity("User", userId))
                .isSeedData();

        Map<String, List<EvLog>> byPlace = evLogRepository.findAllByCarId(carId).stream()
                .filter(log -> isSeedUser || log.isIncludeInStatistics())
                .filter(log -> log.isLoggedWithin(from, to))
                .filter(log -> log.getGeohash() != null && !log.getGeohash().isBlank())
                .sorted(Comparator.comparing(EvLog::getLoggedAt).reversed())
                .collect(Collectors.groupingBy(EvLog::getGeohash, LinkedHashMap::new, Collectors.toList()));
        if (byPlace.isEmpty()) return List.of();

        String homeGeohash = savingsQueryRepository.homeGeohash(userId);
        return byPlace.entrySet().stream()
                .map(e -> toPlace(e.getKey(), e.getValue(), homeGeohash))
                .sorted(MOST_MONEY_FIRST)
                .toList();
    }

    /** logs: alle Ladungen des Orts, neueste zuerst (bei Gleichstand gewinnt die neuere Angabe). */
    private static ChargingPlaceResponse toPlace(String geohash, List<EvLog> logs, String homeGeohash) {
        List<EvLog> paid = logs.stream().filter(ChargingPlacesService::isPaidWithEnergy).toList();
        BigDecimal pricePerKwh = paid.isEmpty() ? null
                : sum(paid, EvLog::getCostEur).divide(sum(paid, ChargingPlacesService::energy), 3, RoundingMode.HALF_UP);

        String cpoName = mostFrequent(logs.stream().map(ChargingPlacesService::cpoOf).filter(Objects::nonNull));
        Stream<EvLog> typeBasis = cpoName == null ? logs.stream()
                : logs.stream().filter(l -> cpoName.equals(cpoOf(l)));
        ChargingType chargingType = mostFrequent(typeBasis.map(EvLog::getChargingType)
                .filter(t -> t == ChargingType.AC || t == ChargingType.DC));

        EvLog newest = logs.get(0);
        return new ChargingPlaceResponse(geohash, kindOf(geohash, logs, homeGeohash), logs.size(),
                sum(logs, ChargingPlacesService::energy), sum(logs, EvLog::getCostEur), pricePerKwh,
                newest.getLoggedAt(), newest.getId(), cpoName, chargingType);
    }

    /** Belegt öffentlich gewinnt über unbekannt: eine einzige Angabe sagt mehr als keine. */
    private static Kind kindOf(String geohash, List<EvLog> logs, String homeGeohash) {
        if (geohash.equals(homeGeohash)) return Kind.HOME;
        if (logs.stream().anyMatch(EvLog::isPublicChargingConfirmed)) return Kind.PUBLIC;
        if (logs.stream().anyMatch(EvLog::isHomeChargingConfirmed)) return Kind.PRIVATE;
        return Kind.UNKNOWN;
    }

    /** Wie der Betreibervergleich: Säulen-kWh, ohne sie die Fahrzeug-kWh. */
    private static BigDecimal energy(EvLog log) {
        BigDecimal charger = log.getKwhCharged();
        return charger != null && charger.signum() > 0 ? charger : log.getKwhAtVehicle();
    }

    /** Kosten 0 zählt nicht beim Preis, Kosten ohne kWh verzerren ihn nicht. */
    private static boolean isPaidWithEnergy(EvLog log) {
        BigDecimal energy = energy(log);
        return log.getCostEur() != null && log.getCostEur().signum() > 0 && energy != null && energy.signum() > 0;
    }

    private static String cpoOf(EvLog log) {
        String name = log.getCpoName();
        return name == null || name.isBlank() ? null : name.trim();
    }

    private static BigDecimal sum(List<EvLog> logs, Function<EvLog, BigDecimal> value) {
        return logs.stream().map(value).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Häufigster Wert, bei Gleichstand der zuerst gesehene. null ohne Werte. */
    private static <T> T mostFrequent(Stream<T> values) {
        Map<T, Long> counts = values.collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.<T, Long>comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
