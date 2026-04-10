package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.application.consumption.ConsumptionMath;
import com.evmonitor.domain.*;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only statistics service: user statistics, community aggregates, seasonal analysis.
 * No CRUD, no side-effects. All write operations remain in EvLogService.
 *
 * Dependencies (azyklisch):
 *   EvLogStatisticsService → ConsumptionCalculationService (pure calculation)
 *   EvLogStatisticsService does NOT depend on EvLogService.
 */
@Service
@RequiredArgsConstructor
public class EvLogStatisticsService {

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final SessionGroupService sessionGroupService;
    private final ConsumptionCalculationService calculationService;
    private final PlausibilityProperties plausibility;

    /**
     * Returns all logs for a car where the calculated consumption is implausible.
     * Uses the same calculation logic as getStatistics() — no duplicate computation.
     */
    public List<EvLogResponse> getImplausibleLogs(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        List<EvLog> allLogs = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        if (car.getBatteryCapacityKwh() == null) return List.of();

        Map<UUID, ConsumptionResult> consumptionByLog =
                calculationService.calculateConsumptionPerLog(allLogs, calculationService.buildCapacityLookup(car), calculationService.lookupWltp(car));

        return allLogs.stream()
                .map(log -> {
                    ConsumptionResult cr = consumptionByLog.get(log.getId());
                    return cr != null && !cr.plausible() ? EvLogResponse.fromDomain(log, cr, cr.distanceKm()) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public List<GeohashResponse> getGeohashData(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }
        return evLogRepository.findGeohashDataByCarId(carId).stream()
                .map(p -> new GeohashResponse(p.geohash(), p.kwhCharged()))
                .toList();
    }

    /**
     * Get statistics for a specific car.
     * Includes key metrics for charging events and charge over time data.
     *
     * @param carId The car ID
     * @param userId The user ID (for ownership verification)
     * @param startDate Optional filter: start date (inclusive)
     * @param endDate Optional filter: end date (inclusive)
     * @param groupBy Aggregation level: DAY, WEEK, or MONTH
     */
    @Transactional(readOnly = true)
    public EvLogStatisticsResponse getStatistics(UUID carId, UUID userId,
            java.time.LocalDate startDate, java.time.LocalDate endDate, String groupBy) {
        // Verify ownership
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> NotFoundException.forEntity("Car", carId));

        if (!car.getUserId().equals(userId)) {
            throw ForbiddenException.notOwner("Car", carId);
        }

        // Check if user is a seed user (for demo mode)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.forEntity("User", userId));
        boolean isSeedUser = user.isSeedData();

        // Load all logs once, sorted — needed as full context for consumption calculations
        List<EvLog> allLogsForCar = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .toList();

        // Derive stats-filtered + time-filtered view without a second DB call
        // Demo Mode: Seed users see ALL their logs (including their own seed data)
        List<EvLog> logs = allLogsForCar.stream()
                .filter(log -> isSeedUser || log.isIncludeInStatistics())
                .filter(log -> log.isLoggedWithin(startDate, endDate))
                .toList();

        // Session groups (WALLBOX_GOE/API_UPLOAD Ladegruppen) — stored in separate table,
        // their sub-sessions have include_in_statistics=false to avoid double-counting.
        // Must be included here so energy/cost from go-e charger sessions appear in statistics.
        List<SessionGroupResponse> sessionGroups = sessionGroupService.findAllByCarId(carId).stream()
                .filter(g -> {
                    java.time.LocalDate groupDate = g.sessionStart().toLocalDate();
                    return (startDate == null || !groupDate.isBefore(startDate))
                            && (endDate == null || !groupDate.isAfter(endDate));
                })
                .toList();

        if (logs.isEmpty() && sessionGroups.isEmpty()) {
            return createEmptyStatistics();
        }

        // Compute per-log consumption once — used for both chart data and overall average
        Map<UUID, ConsumptionResult> consumptionByLog = new LinkedHashMap<>(car.getBatteryCapacityKwh() != null
                ? calculationService.calculateConsumptionPerLog(allLogsForCar, calculationService.buildCapacityLookup(car), calculationService.lookupWltp(car))
                : Map.of());

        // Fallback: for logs with distance but no SoC-based consumption, estimate via kWh/distance.
        // Must run before groupChargesByPeriod() so chart data is populated even without SoC.
        Map<UUID, Integer> distanceByLogId = calculationService.computeDistanceByLogId(allLogsForCar);
        for (EvLog log : allLogsForCar) {
            if (consumptionByLog.containsKey(log.getId())) continue;
            if (log.getKwhCharged() == null) continue;
            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;
            double c = calculationService.effectiveKwhForConsumption(log).doubleValue() / dist * 100.0;
            if (c < plausibility.getAbsoluteMinKwhPer100km() || c > plausibility.getAbsoluteMaxKwhPer100km()) continue;
            consumptionByLog.put(log.getId(), new ConsumptionResult(
                    BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), true, dist, true));
        }

        // Calculate key metrics
        BigDecimal totalKwhCharged = logs.stream()
                .map(EvLog::getKwhCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCostEur = logs.stream()
                .map(EvLog::getCostEur)
                .filter(c -> c != null && c.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // For avgCostPerKwh: normalize AT_VEHICLE logs to AT_CHARGER equivalent — because cost_eur
        // reflects what was billed at the charger, not what entered the battery.
        BigDecimal totalKwhForCost = logs.stream()
                .map(calculationService::effectiveKwhForCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgCostPerKwh = totalKwhForCost.compareTo(BigDecimal.ZERO) > 0
                ? totalCostEur.divide(totalKwhForCost, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal cheapestCharge = logs.stream()
                .map(EvLog::getCostEur)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal mostExpensiveCharge = logs.stream()
                .map(EvLog::getCostEur)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        Integer avgChargeDuration = (int) logs.stream()
                .map(EvLog::getChargeDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        // Charge over time data (grouped and sorted by time)
        List<EvLogStatisticsResponse.ChargeDataPoint> chargesOverTime =
                groupChargesByPeriod(logs, groupBy != null ? groupBy : "MONTH", consumptionByLog);

        // Merge session groups (WALLBOX_GOE Ladegruppen) into stats — kWh, cost, chart data
        if (!sessionGroups.isEmpty()) {
            BigDecimal sgKwh = sessionGroups.stream()
                    .map(SessionGroupResponse::totalKwhCharged)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sgCost = sessionGroups.stream()
                    .map(SessionGroupResponse::costEur)
                    .filter(g -> g != null && g.compareTo(BigDecimal.ZERO) > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalKwhCharged = totalKwhCharged.add(sgKwh);
            totalCostEur = totalCostEur.add(sgCost);
            totalKwhForCost = totalKwhForCost.add(sgKwh);
            avgCostPerKwh = totalKwhForCost.compareTo(BigDecimal.ZERO) > 0
                    ? totalCostEur.divide(totalKwhForCost, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String effectiveGroupBy = groupBy != null ? groupBy : "MONTH";
            Map<String, EvLogStatisticsResponse.ChargeDataPoint> periodMap = chargesOverTime.stream()
                    .collect(Collectors.toMap(
                            dp -> getPeriodKey(dp.timestamp(), effectiveGroupBy),
                            dp -> dp,
                            (a, b) -> a,
                            java.util.LinkedHashMap::new));

            for (SessionGroupResponse group : sessionGroups) {
                String key = getPeriodKey(group.sessionStart(), effectiveGroupBy);
                BigDecimal gKwh = group.totalKwhCharged() != null ? group.totalKwhCharged() : BigDecimal.ZERO;
                BigDecimal gCost = group.costEur() != null ? group.costEur() : BigDecimal.ZERO;
                EvLogStatisticsResponse.ChargeDataPoint existing = periodMap.get(key);
                if (existing == null) {
                    periodMap.put(key, new EvLogStatisticsResponse.ChargeDataPoint(
                            group.sessionStart(), gCost, gKwh, null, null));
                } else {
                    periodMap.put(key, new EvLogStatisticsResponse.ChargeDataPoint(
                            existing.timestamp(),
                            existing.costEur().add(gCost),
                            existing.kwhCharged().add(gKwh),
                            existing.distanceKm(),
                            existing.consumptionKwhPer100km()));
                }
            }

            chargesOverTime = periodMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();
        }

        // Total distance and avg consumption — SoC-based from pre-computed consumptionByLog
        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalDist = 0;        // all logs with consumption data, for avg consumption calculation
        int totalDistAll = 0;     // all logs with distance, for display
        int estimatedCount = 0;   // count logs with estimated consumption (kWh/distance fallback)
        for (EvLog log : logs) {
            ConsumptionResult cr = consumptionByLog.get(log.getId());
            if (cr == null) continue;
            if (cr.distanceKm() > 0) totalDistAll += cr.distanceKm();
            if (cr.estimated()) estimatedCount++;
            totalWeighted = totalWeighted.add(cr.value().multiply(BigDecimal.valueOf(cr.distanceKm())));
            totalDist += cr.distanceKm();
        }
        BigDecimal totalDistanceKm = totalDistAll > 0 ? BigDecimal.valueOf(totalDistAll) : null;
        BigDecimal avgConsumptionKwhPer100km = ConsumptionMath.weightedAverage(totalWeighted, totalDist);

        // Fallback: if no SoC data available, use kWh/distance (distanceByLogId already computed above)
        if (avgConsumptionKwhPer100km == null) {
            List<EvLog> logsWithDistance = logs.stream()
                    .filter(l -> distanceByLogId.containsKey(l.getId()) && distanceByLogId.get(l.getId()) > 0)
                    .toList();
            if (!logsWithDistance.isEmpty()) {
                int totalDistInt = logsWithDistance.stream().mapToInt(l -> distanceByLogId.get(l.getId())).sum();
                totalDistanceKm = BigDecimal.valueOf(totalDistInt);
                avgConsumptionKwhPer100km = calculationService.calculateConsumptionFallback(logsWithDistance, totalDistanceKm);
                estimatedCount = logsWithDistance.size(); // all are estimated in pure fallback mode
            }
        }

        // Seasonal consumption — uses ALL logs (not time-filtered) for best signal
        SeasonalConsumptionResult seasonal = calculateSeasonalConsumption(List.of(car), isSeedUser);

        return new EvLogStatisticsResponse(
                totalKwhCharged,
                totalCostEur,
                avgCostPerKwh,
                cheapestCharge,
                mostExpensiveCharge,
                avgChargeDuration,
                logs.size() + sessionGroups.size(),
                totalDistanceKm,
                avgConsumptionKwhPer100km,
                estimatedCount,
                seasonal.summerConsumptionKwhPer100km(),
                seasonal.winterConsumptionKwhPer100km(),
                chargesOverTime
        );
    }

    private EvLogStatisticsResponse createEmptyStatistics() {
        return new EvLogStatisticsResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                null, null, 0, null, null, List.of()
        );
    }

    private record PlausibleEntry(EvLog log, BigDecimal consumptionKwhPer100km, int distanceKm, boolean estimated) {}

    /**
     * Returns all plausible consumption entries for a car: SoC-based first, fallback if no SoC data.
     * Encapsulates the two-path logic shared by community avg and seasonal calculations.
     */
    private List<PlausibleEntry> getPlausibleEntriesForCar(Car car, List<EvLog> allLogs, List<EvLog> statsLogs) {
        Map<UUID, ConsumptionResult> perLog = calculationService.calculateConsumptionPerLog(
                allLogs, calculationService.buildCapacityLookup(car), calculationService.lookupWltp(car));

        List<PlausibleEntry> entries = new ArrayList<>();

        // Pass 1: SoC-based consumption (plausible only)
        for (EvLog log : statsLogs) {
            ConsumptionResult cr = perLog.get(log.getId());
            if (cr == null || !cr.plausible()) continue;
            entries.add(new PlausibleEntry(log, cr.value(), cr.distanceKm(), false));
        }

        // Pass 2: Fallback kWh/distance for logs without SoC result (hybrid approach)
        // This allows using old Sprit-Monitor imports without SoC data alongside newer logs with SoC.
        // Marked as estimated (less accurate, but better than nothing).
        Map<UUID, Integer> distanceByLogId = calculationService.computeDistanceByLogId(allLogs);
        for (EvLog log : statsLogs) {
            // Skip if already computed via SoC (plausible)
            if (perLog.containsKey(log.getId()) && perLog.get(log.getId()).plausible()) continue;

            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;

            double c = calculationService.effectiveKwhForConsumption(log).doubleValue() / dist * 100;
            if (c < plausibility.getAbsoluteMinKwhPer100km() || c > plausibility.getAbsoluteMaxKwhPer100km()) continue;

            entries.add(new PlausibleEntry(log, BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), dist, true));
        }
        return entries;
    }

    /**
     * Calculates the community average consumption (kWh/100km) for a list of cars.
     *
     * Uses the same logic as per-user statistics (SoC-based → fallback), applied per car.
     * Results are distance-weighted across all cars to give a fair community average.
     *
     * isSeedUser=true: includes all logs (for demo mode).
     * isSeedUser=false: only logs with includeInStatistics=true.
     *
     * Trips with implausible consumption (outside absoluteMin/Max) are excluded
     * in the fallback path via per-trip filtering with PlausibilityProperties.
     *
     * @return distance-weighted avg kWh/100km, or null if no valid data
     */
    public CommunityConsumptionResult calculateCommunityAvgConsumption(List<Car> cars, boolean isSeedUser) {
        if (cars.isEmpty()) return CommunityConsumptionResult.EMPTY;

        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        Map<UUID, List<EvLog>> logsByCarId = evLogRepository.findAllByCarIds(carIds).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.groupingBy(EvLog::getCarId));

        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalDistance = 0;
        int tripCount = 0;
        int estimatedTripCount = 0;

        for (Car car : cars) {
            List<EvLog> allLogs = logsByCarId.getOrDefault(car.getId(), List.of());
            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .toList();
            if (statsLogs.isEmpty()) continue;

            List<PlausibleEntry> entries = getPlausibleEntriesForCar(car, allLogs, statsLogs);
            for (PlausibleEntry e : entries) {
                totalWeighted = totalWeighted.add(e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm())));
                totalDistance += e.distanceKm();
                if (e.estimated()) estimatedTripCount++;
            }
            tripCount += entries.size();
        }

        return new CommunityConsumptionResult(ConsumptionMath.weightedAverage(totalWeighted, totalDistance), tripCount, estimatedTripCount);
    }

    /**
     * Calculates seasonal (summer/winter) community consumption for a list of cars.
     * Summer: April-September (months 4-9), Winter: October-March (months 1-3, 10-12).
     *
     * Uses the same SoC-based logic as calculateCommunityAvgConsumption(), bucketed by season.
     * Fallback (kWh/distance) is applied per-car when no SoC data is available.
     *
     * @return SeasonalConsumptionResult with nullable consumptions if no data for a season
     */
    public SeasonalConsumptionResult calculateSeasonalConsumption(List<Car> cars, boolean isSeedUser) {
        if (cars.isEmpty()) return new SeasonalConsumptionResult(null, null, null, 0, 0, 0, 0);

        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        Map<UUID, List<EvLog>> logsByCarId = evLogRepository.findAllByCarIds(carIds).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.groupingBy(EvLog::getCarId));

        BigDecimal summerWeighted = BigDecimal.ZERO;
        BigDecimal winterWeighted = BigDecimal.ZERO;
        BigDecimal totalWeighted = BigDecimal.ZERO;
        int summerKm = 0, winterKm = 0, totalKm = 0, summerLogCount = 0, winterLogCount = 0;

        for (Car car : cars) {
            List<EvLog> allLogs = logsByCarId.getOrDefault(car.getId(), List.of());
            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .toList();
            if (statsLogs.isEmpty()) continue;

            for (PlausibleEntry e : getPlausibleEntriesForCar(car, allLogs, statsLogs)) {
                BigDecimal weighted = e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm()));
                int month = e.log().getLoggedAt().getMonthValue();
                totalWeighted = totalWeighted.add(weighted);
                totalKm += e.distanceKm();
                if (month >= 5 && month <= 8) {
                    summerWeighted = summerWeighted.add(weighted);
                    summerKm += e.distanceKm();
                    summerLogCount++;
                } else if (month == 11 || month == 12 || month == 1 || month == 2) {
                    winterWeighted = winterWeighted.add(weighted);
                    winterKm += e.distanceKm();
                    winterLogCount++;
                }
            }
        }

        return new SeasonalConsumptionResult(
                ConsumptionMath.weightedAverage(summerWeighted, summerKm),
                ConsumptionMath.weightedAverage(winterWeighted, winterKm),
                ConsumptionMath.weightedAverage(totalWeighted, totalKm),
                summerKm, winterKm, summerLogCount, winterLogCount);
    }

    /**
     * Group charges by time period (DAY, WEEK, MONTH) and aggregate metrics.
     * Uses pre-computed SoC-based ConsumptionResult values for distance and consumption per period,
     * consistent with the overall statistics calculation.
     */
    private List<EvLogStatisticsResponse.ChargeDataPoint> groupChargesByPeriod(
            List<EvLog> logs, String groupBy, Map<UUID, ConsumptionResult> consumptionByLog) {

        Map<String, List<EvLog>> groupedLogs = new java.util.LinkedHashMap<>();
        for (EvLog log : logs) {
            String periodKey = getPeriodKey(log.getLoggedAt(), groupBy);
            groupedLogs.computeIfAbsent(periodKey, k -> new ArrayList<>()).add(log);
        }

        return groupedLogs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<EvLog> periodLogs = entry.getValue();

                    BigDecimal totalKwh = periodLogs.stream()
                            .map(EvLog::getKwhCharged)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalCost = periodLogs.stream()
                            .map(EvLog::getCostEur)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Distance and consumption: SoC-based, plausible logs only
                    BigDecimal periodWeighted = BigDecimal.ZERO;
                    int periodDist = 0;
                    for (EvLog log : periodLogs) {
                        ConsumptionResult cr = consumptionByLog.get(log.getId());
                        if (cr == null) continue;
                        periodWeighted = periodWeighted.add(cr.value().multiply(BigDecimal.valueOf(cr.distanceKm())));
                        periodDist += cr.distanceKm();
                    }

                    BigDecimal periodDistance = periodDist > 0 ? BigDecimal.valueOf(periodDist) : null;
                    BigDecimal periodConsumption = ConsumptionMath.weightedAverage(periodWeighted, periodDist);

                    LocalDateTime periodTimestamp = periodLogs.get(0).getLoggedAt();
                    return new EvLogStatisticsResponse.ChargeDataPoint(
                            periodTimestamp, totalCost, totalKwh, periodDistance, periodConsumption
                    );
                })
                .toList();
    }

    /**
     * Generate a period key for grouping (e.g., "2025-02", "2025-W08", "2025-02-15").
     */
    private String getPeriodKey(LocalDateTime timestamp, String groupBy) {
        java.time.LocalDate date = timestamp.toLocalDate();

        return switch (groupBy.toUpperCase()) {
            case "DAY" -> date.toString(); // "2025-02-15"
            case "WEEK" -> {
                // ISO week number
                int year = date.getYear();
                int week = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                yield String.format("%d-W%02d", year, week);
            }
            case "MONTH" -> String.format("%d-%02d", date.getYear(), date.getMonthValue());
            default -> String.format("%d-%02d", date.getYear(), date.getMonthValue());
        };
    }

    /**
     * Returns the cost per kWh from the most recent log at the same geohash for this user.
     * Used to pre-fill the price field in the log form.
     */
    public Optional<BigDecimal> getPriceSuggestion(UUID userId, double latitude, double longitude, boolean isPublicCharging) {
        int precision = isPublicCharging ? 7 : 6;
        String geohash = GeoHash.withCharacterPrecision(latitude, longitude, precision).toBase32();
        return evLogRepository.findMostRecentCostPerKwhByUserIdAndGeohash(userId, geohash);
    }
}
