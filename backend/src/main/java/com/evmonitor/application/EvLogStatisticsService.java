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

    private static final int MIN_TRIPS_FOR_CAR_RANGE = 5;

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
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
                .map(p -> new GeohashResponse(p.geohash(), p.kwh()))
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

        if (logs.isEmpty()) {
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
            if (!log.hasEnergyData()) continue;
            Integer dist = distanceByLogId.get(log.getId());
            if (dist == null || dist < plausibility.getMinTripDistanceKm()) continue;
            double c = calculationService.effectiveKwhForConsumption(log).doubleValue() / dist * 100.0;
            if (c < plausibility.getAbsoluteMinKwhPer100km() || c > plausibility.getAbsoluteMaxKwhPer100km()) continue;
            consumptionByLog.put(log.getId(), new ConsumptionResult(
                    BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP), true, dist, CalculationQuality.KWH_ESTIMATED));
        }

        // Calculate key metrics
        BigDecimal totalKwhCharged = logs.stream()
                .map(l -> l.getKwhCharged() != null ? l.getKwhCharged() : l.getKwhAtVehicle())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCostEur = logs.stream()
                .map(EvLog::getCostEur)
                .filter(c -> c != null && c.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // For avgCostPerKwh: normalize AT_VEHICLE logs to AT_CHARGER equivalent — because cost_eur
        // reflects what was billed at the charger, not what entered the battery.
        // Skip logs without any energy data to avoid NPE in effectiveKwhForCost.
        BigDecimal totalKwhForCost = logs.stream()
                .filter(l -> l.getKwhCharged() != null || l.getKwhAtVehicle() != null)
                .map(calculationService::effectiveKwhForCost)
                .filter(Objects::nonNull)
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

        // Total distance and avg consumption — SoC-based from pre-computed consumptionByLog
        // Bug-fix: iterate consumptionByLog.entrySet() with isLoggedWithin filter so time-filtered
        // views don't include consumption data from outside the selected period.
        Map<UUID, EvLog> logById = allLogsForCar.stream()
                .collect(Collectors.toMap(EvLog::getId, l -> l));
        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalDist = 0;        // all logs with consumption data, for avg consumption calculation
        int totalDistAll = 0;     // all logs with distance, for display
        int estimatedCount = 0;   // count logs with estimated consumption (kWh/distance fallback)
        for (Map.Entry<UUID, ConsumptionResult> entry : consumptionByLog.entrySet()) {
            EvLog log = logById.get(entry.getKey());
            if (log == null || !log.isLoggedWithin(startDate, endDate)) continue;
            ConsumptionResult cr = entry.getValue();
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

        // Peer benchmark — only when car has a vehicle spec linked
        EvLogStatisticsResponse.PeerBenchmark peerBenchmark = null;
        if (car.getVehicleSpecificationId() != null) {
            peerBenchmark = buildPeerBenchmark(car, user, allLogsForCar, isSeedUser);
        }

        return new EvLogStatisticsResponse(
                totalKwhCharged,
                totalCostEur,
                avgCostPerKwh,
                cheapestCharge,
                mostExpensiveCharge,
                avgChargeDuration,
                logs.size(),
                totalDistanceKm,
                avgConsumptionKwhPer100km,
                estimatedCount,
                seasonal.summerConsumptionKwhPer100km(),
                seasonal.winterConsumptionKwhPer100km(),
                chargesOverTime,
                peerBenchmark
        );
    }

    private EvLogStatisticsResponse createEmptyStatistics() {
        return new EvLogStatisticsResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                null, null, 0, null, null, List.of(), null
        );
    }

    private EvLogStatisticsResponse.PeerBenchmark buildPeerBenchmark(
            Car currentCar, User currentUser, List<EvLog> allLogsForCurrentCar, boolean isSeedUser) {

        List<Car> allCarsWithSpec = carRepository.findAllByVehicleSpecificationId(currentCar.getVehicleSpecificationId());
        List<Car> peerCars = allCarsWithSpec.stream()
                .filter(c -> !c.getUserId().equals(currentCar.getUserId()))
                .toList();

        if (peerCars.isEmpty()) return null;

        List<UUID> peerUserIds = peerCars.stream().map(Car::getUserId).distinct().toList();
        List<User> peerUsers = userRepository.findAllByIds(peerUserIds);
        Map<UUID, User> peerUserById = peerUsers.stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Car> nonSeedPeerCars = peerCars.stream()
                .filter(c -> {
                    User u = peerUserById.get(c.getUserId());
                    return u != null && !u.isSeedData();
                })
                .toList();

        if (nonSeedPeerCars.isEmpty()) return null;

        // Peer consumption
        CommunityConsumptionResult peerConsumption = calculateCommunityAvgConsumption(nonSeedPeerCars, false);

        // User lifetime consumption (all logs, no time filter)
        List<EvLog> userStatsLogs = allLogsForCurrentCar.stream()
                .filter(l -> isSeedUser || l.isIncludeInStatistics())
                .toList();
        BigDecimal userLifetimeConsumption = null;
        List<PlausibleEntry> userEntries = getPlausibleEntriesForCar(currentCar, allLogsForCurrentCar, userStatsLogs);
        if (!userEntries.isEmpty()) {
            BigDecimal weighted = BigDecimal.ZERO;
            int dist = 0;
            for (PlausibleEntry e : userEntries) {
                weighted = weighted.add(e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm())));
                dist += e.distanceKm();
            }
            userLifetimeConsumption = ConsumptionMath.weightedAverage(weighted, dist);
        }

        // User lifetime cost/kWh
        BigDecimal userLifetimeCostPerKwh = null;
        BigDecimal totalUserCost = BigDecimal.ZERO;
        BigDecimal totalUserKwh = BigDecimal.ZERO;
        for (EvLog log : userStatsLogs) {
            if (log.getCostEur() == null) continue;
            BigDecimal kwh = calculationService.effectiveKwhForCost(log);
            if (kwh == null || kwh.compareTo(BigDecimal.ZERO) <= 0) continue;
            totalUserCost = totalUserCost.add(log.getCostEur());
            totalUserKwh = totalUserKwh.add(kwh);
        }
        if (totalUserKwh.compareTo(BigDecimal.ZERO) > 0) {
            userLifetimeCostPerKwh = totalUserCost.divide(totalUserKwh, 4, RoundingMode.HALF_UP);
        }

        // Peer cost — same-country peers only (min 3 unique users)
        String currentUserCountry = currentUser.getCountry();
        Set<UUID> sameCountryUserIds = peerUsers.stream()
                .filter(u -> currentUserCountry != null && currentUserCountry.equals(u.getCountry()) && !u.isSeedData())
                .map(User::getId)
                .collect(Collectors.toSet());
        int sameCountryPeerUsers = (int) nonSeedPeerCars.stream()
                .filter(c -> sameCountryUserIds.contains(c.getUserId()))
                .map(Car::getUserId).distinct().count();

        BigDecimal peerAvgCostPerKwh = null;
        if (sameCountryPeerUsers >= 3) {
            Set<UUID> sameCountryCarIds = nonSeedPeerCars.stream()
                    .filter(c -> sameCountryUserIds.contains(c.getUserId()))
                    .map(Car::getId)
                    .collect(Collectors.toSet());
            List<UUID> peerCarIds = nonSeedPeerCars.stream().map(Car::getId).toList();
            BigDecimal totalPeerCost = BigDecimal.ZERO;
            BigDecimal totalPeerKwh = BigDecimal.ZERO;
            for (EvLog log : evLogRepository.findAllByCarIds(peerCarIds)) {
                if (!sameCountryCarIds.contains(log.getCarId())) continue;
                if (!log.isIncludeInStatistics()) continue;
                if (log.getCostEur() == null) continue;
                BigDecimal kwh = calculationService.effectiveKwhForCost(log);
                if (kwh == null || kwh.compareTo(BigDecimal.ZERO) <= 0) continue;
                totalPeerCost = totalPeerCost.add(log.getCostEur());
                totalPeerKwh = totalPeerKwh.add(kwh);
            }
            if (totalPeerKwh.compareTo(BigDecimal.ZERO) > 0) {
                peerAvgCostPerKwh = totalPeerCost.divide(totalPeerKwh, 4, RoundingMode.HALF_UP);
            }
        }

        long uniquePeerUsers = nonSeedPeerCars.stream().map(Car::getUserId).distinct().count();
        boolean sufficientData = uniquePeerUsers >= 3 && peerConsumption.tripCount() >= 10;

        return new EvLogStatisticsResponse.PeerBenchmark(
                userLifetimeConsumption,
                peerConsumption.value(),
                userLifetimeCostPerKwh,
                peerAvgCostPerKwh,
                (int) uniquePeerUsers,
                peerConsumption.tripCount(),
                sameCountryPeerUsers,
                currentUserCountry,
                sufficientData
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
            if (!log.hasEnergyData()) continue;

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
        List<BigDecimal> perCarAverages = new ArrayList<>();
        List<List<PlausibleEntry>> allCarEntries = new ArrayList<>();

        for (Car car : cars) {
            List<EvLog> allLogs = logsByCarId.getOrDefault(car.getId(), List.of());
            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .toList();
            if (statsLogs.isEmpty()) continue;

            List<PlausibleEntry> entries = getPlausibleEntriesForCar(car, allLogs, statsLogs);
            BigDecimal carWeighted = BigDecimal.ZERO;
            int carDistance = 0;
            for (PlausibleEntry e : entries) {
                BigDecimal weighted = e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm()));
                carWeighted = carWeighted.add(weighted);
                carDistance += e.distanceKm();
                if (e.estimated()) estimatedTripCount++;
            }
            tripCount += entries.size();
            totalWeighted = totalWeighted.add(carWeighted);
            totalDistance += carDistance;
            BigDecimal carAvg = ConsumptionMath.weightedAverage(carWeighted, carDistance);
            if (carAvg != null && entries.size() >= MIN_TRIPS_FOR_CAR_RANGE) perCarAverages.add(carAvg);
            allCarEntries.add(entries);
        }

        Collections.sort(perCarAverages);
        BigDecimal minValue;
        BigDecimal maxValue;
        CommunityConsumptionResult.RangeSource rangeSource;
        if (perCarAverages.size() >= 2) {
            minValue = interpolatedPercentile(perCarAverages, 0.25);
            maxValue = interpolatedPercentile(perCarAverages, 0.75);
            rangeSource = CommunityConsumptionResult.RangeSource.PER_DRIVER;
        } else {
            List<BigDecimal> allTripConsumptions = allCarEntries.stream()
                    .flatMap(Collection::stream)
                    .map(PlausibleEntry::consumptionKwhPer100km)
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new));
            if (allTripConsumptions.size() >= MIN_TRIPS_FOR_CAR_RANGE) {
                minValue = interpolatedPercentile(allTripConsumptions, 0.25);
                maxValue = interpolatedPercentile(allTripConsumptions, 0.75);
                rangeSource = CommunityConsumptionResult.RangeSource.PER_TRIP;
            } else {
                minValue = null;
                maxValue = null;
                rangeSource = null;
            }
        }

        return new CommunityConsumptionResult(ConsumptionMath.weightedAverage(totalWeighted, totalDistance), minValue, maxValue, rangeSource, tripCount, estimatedTripCount);
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
                            .filter(Objects::nonNull)
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
     * Returns a price suggestion for the log form based on the most recent log at the same geohash.
     * Includes the cost per kWh and the charging provider (tariff) that was used there.
     */
    // Linear interpolation: avoids single outliers dominating the displayed consumption range
    private static BigDecimal interpolatedPercentile(List<BigDecimal> sorted, double p) {
        int n = sorted.size();
        if (n == 1) return sorted.get(0).setScale(2, RoundingMode.HALF_UP);
        double pos = p * (n - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        if (lower == upper) return sorted.get(lower).setScale(2, RoundingMode.HALF_UP);
        double fraction = pos - lower;
        BigDecimal lv = sorted.get(lower);
        BigDecimal uv = sorted.get(upper);
        return lv.add(uv.subtract(lv).multiply(BigDecimal.valueOf(fraction)))
                 .setScale(2, RoundingMode.HALF_UP);
    }

    public Optional<PriceSuggestion> getPriceSuggestion(UUID userId, double latitude, double longitude, boolean isPublicCharging) {
        int precision = isPublicCharging ? 7 : 6;
        String geohash = GeoHash.withCharacterPrecision(latitude, longitude, precision).toBase32();
        return evLogRepository.findMostRecentLogAtGeohash(userId, geohash)
                .filter(log -> log.getCostEur() != null && log.getCostEur().compareTo(BigDecimal.ZERO) > 0)
                .flatMap(log -> {
                    BigDecimal kwh = calculationService.effectiveKwhForCost(log);
                    if (kwh == null || kwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
                    return Optional.of(new PriceSuggestion(
                            log.getCostEur().divide(kwh, 4, RoundingMode.HALF_UP),
                            log.getChargingProviderId()));
                });
    }
}
