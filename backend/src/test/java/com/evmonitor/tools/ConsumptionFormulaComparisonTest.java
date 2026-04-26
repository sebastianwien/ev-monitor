package com.evmonitor.tools;

import com.evmonitor.application.ConsumptionResult;
import com.evmonitor.application.PlausibilityProperties;
import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.domain.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vergleichsanalyse alte vs. neue Verbrauchsformel auf echten Prod-Daten.
 *
 * Läuft gegen die lokale postgres (Profil "dev").
 * Kein Test-Assert - gibt Ergebnisse auf stdout aus.
 *
 * Ausführen: ./gradlew test --tests "*.ConsumptionFormulaComparisonTest" -Dspring.profiles.active=dev
 */
@Disabled("Lokales Analyse-Tool - benötigt lokale Postgres dev DB, nicht für CI")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "jwt.secret=analysis-secret-key-FOR-LOCAL-ANALYSIS-ONLY-MIN-64-CHARS-LONG-HS512-01234567890",
        "spring.flyway.enabled=false"
})
class ConsumptionFormulaComparisonTest {

    private static final List<String> TARGET_EMAILS = List.of(
            "sebastian.wien@posteo.de"
    );

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private EvLogRepository evLogRepository;

    @Autowired
    private ConsumptionCalculationService calc;

    @Autowired
    private PlausibilityProperties plausibilityProps;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Test
    void compareOldVsNewFormula() {
        for (String email : TARGET_EMAILS) {
            Optional<User> maybeUser = userRepository.findByEmail(email);
            if (maybeUser.isEmpty()) {
                System.out.println("=== USER NOT FOUND: " + email + " ===");
                continue;
            }
            User user = maybeUser.get();
            List<Car> cars = carRepository.findAllByUserId(user.getId());

            System.out.println("\n══════════════════════════════════════════════════════════════");
            System.out.printf("USER: %s%n", email);
            System.out.println("══════════════════════════════════════════════════════════════");

            for (Car car : cars) {
                List<EvLog> allLogs = evLogRepository.findAllByCarId(car.getId())
                        .stream()
                        .filter(EvLog::isIncludeInStatistics)
                        .sorted(Comparator.comparing(EvLog::getLoggedAt))
                        .toList();

                if (allLogs.isEmpty()) continue;

                BigDecimal wltp = calc.lookupWltp(car);
                Function<LocalDate, BigDecimal> capacityLookup = calc.buildCapacityLookup(car);

                System.out.printf("%nAuto: %s %s | Kapazität: %s kWh | WLTP: %s kWh/100km | Logs: %d%n",
                        car.getModel() != null ? car.getModel().getBrand() : "?",
                        car.getModel() != null ? car.getModel() : "?",
                        car.getBatteryCapacityKwh(),
                        wltp != null ? wltp : "n/a",
                        allLogs.size());

                // Neue Formel (kWh-primary, lokaler Stand): echte Service-Berechnung
                Map<UUID, ConsumptionResult> newResults = calc.calculateConsumptionPerLog(allLogs, capacityLookup, wltp);

                // Main-remote Formel (socBefore-primary): exakte Kopie der Service-Logik von origin/main
                Map<UUID, ConsumptionResult> mainRemoteResults = mainRemoteCalculateConsumptionPerLog(allLogs, capacityLookup, wltp);

                // Vergleich pro Log - nur Logs die BEIDE Formeln berechnen konnten
                Map<UUID, EvLog> logById = allLogs.stream()
                        .collect(Collectors.toMap(EvLog::getId, l -> l));
                List<TripDelta> deltas = new ArrayList<>();
                for (Map.Entry<UUID, ConsumptionResult> entry : newResults.entrySet()) {
                    UUID logId = entry.getKey();
                    ConsumptionResult newResult = entry.getValue();
                    ConsumptionResult mainRemote = mainRemoteResults.get(logId);

                    if (mainRemote == null) continue;

                    BigDecimal delta = newResult.value().subtract(mainRemote.value());
                    EvLog log = logById.get(logId);
                    String path = log != null && log.getSocBeforeChargePercent() != null ? "SOC_BEFORE" : "KWH_DERIVED";
                    deltas.add(new TripDelta(logId, log != null ? log.getLoggedAt() : null,
                            mainRemote.value(), newResult.value(), delta,
                            newResult.distanceKm(), newResult.plausible(), path));
                }

                printStats(deltas);
                printAllTrips(deltas);
            }
        }
    }

    /**
     * Exakte Kopie der two-pass Logik aus origin/main ConsumptionCalculationService,
     * mit der socBefore-primary Formel in calculateConsumption().
     */
    private Map<UUID, ConsumptionResult> mainRemoteCalculateConsumptionPerLog(
            List<EvLog> allLogs, Function<LocalDate, BigDecimal> capacityForDate, BigDecimal wltpKwh) {
        List<EvLog> sorted = allLogs.stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        List<UUID> ids = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<Integer> distances = new ArrayList<>();

        sorted.stream()
                .filter(EvLog::isComplete)
                .forEach(logY -> {
                    PreviousLogResult prev = findPreviousLog(sorted, logY);
                    if (prev == null) return;
                    int dist = logY.getOdometerKm() - prev.logX().getOdometerKm();
                    if (dist < plausibilityProps.getMinTripDistanceKm()) return;
                    BigDecimal capacity = capacityForDate.apply(logY.getLoggedAt().toLocalDate());
                    if (capacity == null) return;
                    mainRemoteCalculateConsumption(prev.logX(), logY, capacity, prev.intermediateKwh()).ifPresent(c -> {
                        ids.add(logY.getId());
                        values.add(c);
                        distances.add(dist);
                    });
                });

        Map<UUID, ConsumptionResult> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            boolean plausible = calc.isConsumptionPlausible(values.get(i), values, wltpKwh);
            result.put(ids.get(i), new ConsumptionResult(values.get(i), plausible, distances.get(i)));
        }
        return result;
    }

    /**
     * Exakte Kopie der calculateConsumption()-Implementierung von origin/main (socBefore-primary).
     *
     * socBefore(logY): gespeicherter Wert wenn vorhanden, sonst aus kwhCharged abgeleitet:
     *   socBeforeY = socAfterY - effectiveKwh / capacity * 100
     * energyConsumed = (socAfterX - socBeforeY) * capacity / 100 + intermediateKwh
     */
    private Optional<BigDecimal> mainRemoteCalculateConsumption(
            EvLog logX, EvLog logY, BigDecimal batteryCapacityKwh, BigDecimal intermediateKwh) {
        if (!logX.canBeUsedAsLogX() || !logY.isComplete()) return Optional.empty();
        if (batteryCapacityKwh == null || batteryCapacityKwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        int distance = logY.getOdometerKm() - logX.getOdometerKm();
        if (distance <= 0) return Optional.empty();

        BigDecimal socBeforeLogYPercent = logY.getSocBeforeChargePercent() != null
                ? logY.getSocBeforeChargePercent()
                : logY.getSocAfterChargePercent()
                        .subtract(calc.effectiveKwhForConsumption(logY)
                                .divide(batteryCapacityKwh, 4, RoundingMode.HALF_UP)
                                .multiply(HUNDRED));

        BigDecimal energyConsumedKwh = logX.getSocAfterChargePercent()
                .subtract(socBeforeLogYPercent)
                .multiply(batteryCapacityKwh)
                .divide(HUNDRED, 4, RoundingMode.HALF_UP)
                .add(intermediateKwh);

        if (energyConsumedKwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        return Optional.of(energyConsumedKwh
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(distance), 2, RoundingMode.HALF_UP));
    }

    /**
     * Exakte Kopie von findPreviousLog() aus dem Service (identisch auf beiden Branches).
     * Sucht rückwärts den nächsten gültigen logX-Kandidaten; transparente Logs (go-e Sub-Sessions)
     * werden übersprungen und ihr kWh akkumuliert.
     */
    private PreviousLogResult findPreviousLog(List<EvLog> sortedLogs, EvLog logY) {
        for (int i = 0; i < sortedLogs.size(); i++) {
            if (!sortedLogs.get(i).getId().equals(logY.getId())) continue;
            if (i == 0) return null;

            BigDecimal intermediateKwh = BigDecimal.ZERO;
            for (int j = i - 1; j >= 0; j--) {
                EvLog candidate = sortedLogs.get(j);
                if (candidate.canBeUsedAsLogX()) {
                    return new PreviousLogResult(candidate, intermediateKwh);
                }
                if (candidate.getDataSource().isTransparentForConsumptionChain()) {
                    if (candidate.hasKwhCharged()) {
                        intermediateKwh = intermediateKwh.add(candidate.getKwhCharged());
                    }
                } else {
                    return null;
                }
            }
            return null;
        }
        return null;
    }

    private record PreviousLogResult(EvLog logX, BigDecimal intermediateKwh) {}

    private void printStats(List<TripDelta> deltas) {
        if (deltas.isEmpty()) {
            System.out.println("  Keine vergleichbaren Trips.");
            return;
        }

        List<BigDecimal> deltaValues = deltas.stream().map(TripDelta::delta).toList();
        List<BigDecimal> oldValues = deltas.stream().map(TripDelta::oldValue).toList();
        List<BigDecimal> newValues = deltas.stream().map(TripDelta::newValue).toList();
        long plausibleNew = deltas.stream().filter(TripDelta::plausible).count();

        BigDecimal avgDelta = mean(deltaValues);
        BigDecimal stdDevDelta = stdDev(deltaValues, avgDelta);
        BigDecimal avgOld = mean(oldValues);
        BigDecimal avgNew = mean(newValues);

        // Distanzgewichteter Durchschnitt
        int totalKm = deltas.stream().mapToInt(TripDelta::distanceKm).sum();
        BigDecimal weightedOld = deltas.stream()
                .map(d -> d.oldValue().multiply(BigDecimal.valueOf(d.distanceKm())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(totalKm), 2, RoundingMode.HALF_UP);
        BigDecimal weightedNew = deltas.stream()
                .map(d -> d.newValue().multiply(BigDecimal.valueOf(d.distanceKm())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(totalKm), 2, RoundingMode.HALF_UP);

        long socBeforeCount = deltas.stream().filter(d -> "SOC_BEFORE".equals(d.oldPath())).count();
        long kwhDerivedCount = deltas.stream().filter(d -> "KWH_DERIVED".equals(d.oldPath())).count();
        System.out.printf("  Vergleichbare Trips:  %d (SOC_BEFORE: %d, KWH_DERIVED: %d) | Gesamt-km: %,d%n",
                deltas.size(), socBeforeCount, kwhDerivedCount, totalKm);
        System.out.printf("  PROD (socBefore-primary) avg (ungewichtet): %6.2f kWh/100km  |  distanzgew: %6.2f%n", avgOld, weightedOld);
        System.out.printf("  NEU  (kWh-primary)       avg (ungewichtet): %6.2f kWh/100km  |  distanzgew: %6.2f%n", avgNew, weightedNew);
        System.out.printf("  Δ avg (NEU-PROD): %+.2f  |  Δ stddev: %.2f  |  plausibel (neu): %d/%d%n",
                avgDelta, stdDevDelta, plausibleNew, deltas.size());
    }

    private void printAllTrips(List<TripDelta> deltas) {
        if (deltas.isEmpty()) return;
        System.out.printf("  %-10s  %-8s  %-12s  %8s  %8s  %7s  %6s  %s%n",
                "Datum", "logId", "Formel-Pfad", "PROD", "NEU", "Δ", "dist", "plausibel");
        deltas.stream()
                .sorted(Comparator.comparing(d -> d.loggedAt() != null ? d.loggedAt() : java.time.LocalDateTime.MIN))
                .forEach(d -> {
                    String date = d.loggedAt() != null ? d.loggedAt().toLocalDate().toString() : "?";
                    System.out.printf("  %-10s  %-8s  %-12s  %8.2f  %8.2f  %+7.2f  %5d  %s%n",
                            date, d.logId().toString().substring(0, 8), d.oldPath(),
                            d.oldValue(), d.newValue(), d.delta(), d.distanceKm(),
                            d.plausible() ? "ok" : "IMPLAUSIBEL");
                });
    }

    private BigDecimal mean(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal stdDev(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal sumSq = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sumSq.divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_UP)
                .sqrt(new java.math.MathContext(10, RoundingMode.HALF_UP));
    }

    private record TripDelta(UUID logId, java.time.LocalDateTime loggedAt, BigDecimal oldValue,
                              BigDecimal newValue, BigDecimal delta, int distanceKm,
                              boolean plausible, String oldPath) {}
}
