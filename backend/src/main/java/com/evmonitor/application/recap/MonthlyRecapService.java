package com.evmonitor.application.recap;

import com.evmonitor.application.EvLogStatisticsResponse;
import com.evmonitor.application.EvLogStatisticsResponse.LocationSplit;
import com.evmonitor.application.EvLogStatisticsService;
import com.evmonitor.application.recap.MonthlyRecap.PricelessHint;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.VehicleCategory;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.external.FuelPriceService;
import com.evmonitor.infrastructure.persistence.MonthlyRecapQueryRepository;
import com.evmonitor.infrastructure.persistence.MonthlyRecapQueryRepository.RecapCandidate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Baut die Zahlen der Monatsrückblick-Mail.
 *
 * Kosten, kWh, Strecke und Verbrauch kommen aus {@link EvLogStatisticsService#getStatistics},
 * damit die Mail dieselben Werte zeigt wie das Dashboard für denselben Monat. Hier wird nur
 * gezählt (Ladungen, AC/DC, fehlende Preise), nichts neu gerechnet.
 */
@Service
public class MonthlyRecapService {

    /** Durchschnittsverbrauch des Vergleichs-Verbrenners, wie im Ticker. */

    private final EvLogStatisticsService statisticsService;
    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final FuelPriceService fuelPriceService;

    public MonthlyRecapService(EvLogStatisticsService statisticsService, EvLogRepository evLogRepository,
                               CarRepository carRepository, UserRepository userRepository,
                               FuelPriceService fuelPriceService) {
        this.statisticsService = statisticsService;
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.fuelPriceService = fuelPriceService;
    }

    /** Leer, wenn Nutzer oder Auto fehlen oder die Statistik weniger als drei Ladungen kennt. */
    public Optional<MonthlyRecap> build(RecapCandidate candidate, YearMonth month) {
        Optional<User> user = userRepository.findById(candidate.userId());
        Optional<Car> car = carRepository.findById(candidate.carId())
                .filter(c -> c.isOwnedBy(candidate.userId()));
        if (user.isEmpty() || car.isEmpty()) {
            return Optional.empty();
        }

        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        // Gleicher Filter wie die Statistik: nur Logs, die in Auswertungen zählen.
        List<EvLog> logs = evLogRepository.findAllByCarId(car.get().getId()).stream()
                .filter(EvLog::isIncludeInStatistics)
                .filter(l -> l.isLoggedWithin(start, end))
                .toList();
        if (logs.size() < MonthlyRecapQueryRepository.MIN_CHARGES) {
            return Optional.empty();
        }

        EvLogStatisticsResponse stats = statisticsService.getStatistics(
                car.get().getId(), candidate.userId(), start, end, "MONTH");

        List<EvLog> priceless = logs.stream()
                .filter(l -> l.getCostEur() == null)
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();
        BigDecimal cost = priceless.size() == logs.size() ? null : stats.energyCostEur();
        BigDecimal distance = positiveOrNull(stats.totalDistanceKm());
        // Benziner der Fahrzeugklasse des Autos zum Benzinpreis, passend zur Zeile "gegenüber einem Benziner"
        BigDecimal fuelPrice = BigDecimal.valueOf(fuelPriceService.getBenzinPrice());
        VehicleCategory category = car.get().getModel().getCategory();
        BigDecimal fuelLiters = category.getPetrolLitersPer100Km();

        return Optional.of(new MonthlyRecap(
                user.get().getId(),
                car.get().getId(),
                user.get().getEmail(),
                user.get().getUsername(),
                user.get().getRegistrationLocale(),
                month,
                car.get().getModel().getDisplayName(),
                logs.size(),
                count(logs, ChargingType.AC),
                count(logs, ChargingType.DC),
                stats.totalKwhCharged(),
                cost,
                distance,
                stats.avgConsumptionKwhPer100km(),
                homeSharePercent(stats.locationSplit()),
                distance == null || cost == null ? null : fuelCost(distance, fuelLiters, fuelPrice).multiply(pricedShare(logs))
                        .setScale(2, RoundingMode.HALF_UP),
                fuelPrice,
                fuelLiters,
                category,
                priceless.isEmpty() ? null : new PricelessHint(priceless.size(),
                        priceless.get(0).getLoggedAt(), kwhOf(priceless.get(0))),
                previousDistance(candidate, car.get(), month)));
    }

    private BigDecimal previousDistance(RecapCandidate candidate, Car car, YearMonth month) {
        YearMonth previous = month.minusMonths(1);
        EvLogStatisticsResponse stats = statisticsService.getStatistics(
                car.getId(), candidate.userId(), previous.atDay(1), previous.atEndOfMonth(), "MONTH");
        return stats == null ? null : positiveOrNull(stats.totalDistanceKm());
    }

    /**
     * Anteil der kWh aus Ladungen mit Preis. Der Benziner-Vergleich nimmt nur diesen Teil der
     * Strecke, sonst stünden die Stromkosten ohne die preislosen Ladungen gegen den Sprit für die
     * ganze Strecke, und die Ersparnis wäre zu hoch. Gilt nur für die Mail, die Statistik bleibt.
     */
    private static BigDecimal pricedShare(List<EvLog> logs) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal priced = BigDecimal.ZERO;
        for (EvLog log : logs) {
            BigDecimal kwh = kwhOf(log);
            if (kwh == null) {
                continue;
            }
            total = total.add(kwh);
            if (log.getCostEur() != null) {
                priced = priced.add(kwh);
            }
        }
        return total.signum() > 0 ? priced.divide(total, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private static int count(List<EvLog> logs, ChargingType type) {
        return (int) logs.stream().filter(l -> l.getChargingType() == type).count();
    }

    private static BigDecimal kwhOf(EvLog log) {
        return log.getKwhCharged() != null ? log.getKwhCharged() : log.getKwhAtVehicle();
    }

    private static BigDecimal positiveOrNull(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : null;
    }

    private static BigDecimal fuelCost(BigDecimal distanceKm, BigDecimal litersPer100Km, BigDecimal pricePerLiter) {
        return distanceKm.multiply(litersPer100Km).multiply(pricePerLiter)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static Integer homeSharePercent(LocationSplit split) {
        if (split == null || split.privateKwh() == null || split.privateKwh().signum() <= 0) {
            return null;
        }
        BigDecimal total = List.of(split.publicKwh(), split.privateKwh(), split.unknownKwh()).stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int percent = split.privateKwh().multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP).intValue();
        // "Daheim geladen 0 %" wäre eine Zeile ohne Aussage.
        return percent > 0 ? percent : null;
    }
}
