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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final EvTripRepository evTripRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final VehicleSpecificationRepository vehicleSpecificationRepository;
    private final ConsumptionCalculationService calculationService;
    private final PlausibilityProperties plausibility;
    private final FixedCostService fixedCostService;
    private final LocationPricing locationPricing;

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

        if (car.getNominalNetCapacityKwh() == null) return List.of();

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

    /**
     * Returns all logs for a car that have no cost yet (cost_eur IS NULL), newest first.
     * Server-side over ALL logs (not just the loaded feed page), so old cost-less charges - which
     * would otherwise sit invisible far down the paginated feed - stay findable for the "add price"
     * banner. Genuinely free charges (cost 0) are excluded; only truly missing prices show up.
     */
    public List<EvLogResponse> getPricelessLogs(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Targeted query (cost_eur IS NULL, newest first) instead of loading every log into memory:
        // unlike getImplausibleLogs this needs no consumption context, so the DB does the filtering.
        return evLogRepository.findPricelessByCarId(carId).stream()
                .map(EvLogResponse::fromDomain)
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
            FixedCostTotals fixedCosts = (startDate != null && endDate != null)
                    ? fixedCostService.calculateTotalsForPeriod(carId, startDate, endDate)
                    : FixedCostTotals.ZERO;
            BigDecimal distanceFromTrips = (startDate != null || endDate != null)
                    ? computeOdometerDeltaDistance(carId, startDate, endDate)
                    : null;
            return createEmptyStatisticsWithFixedCostAndDistance(fixedCosts, distanceFromTrips);
        }

        // Compute per-log consumption once — used for both chart data and overall average
        Map<UUID, ConsumptionResult> consumptionByLog = new LinkedHashMap<>(car.getNominalNetCapacityKwh() != null
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

        BigDecimal energyCostEur = logs.stream()
                .map(EvLog::getCostEur)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FixedCostTotals fixedCosts = (startDate != null && endDate != null)
                ? fixedCostService.calculateTotalsForPeriod(carId, startDate, endDate)
                : FixedCostTotals.ZERO;

        BigDecimal totalCostEur = energyCostEur.add(fixedCosts.net());

        // For avgCostPerKwh: normalize AT_VEHICLE logs to AT_CHARGER equivalent — because cost_eur
        // reflects what was billed at the charger, not what entered the battery.
        // Skip logs without any energy data to avoid NPE in gridSideKwhEstimate.
        BigDecimal totalKwhForCost = logs.stream()
                .filter(l -> l.getKwhCharged() != null || l.getKwhAtVehicle() != null)
                .map(calculationService::gridSideKwhEstimate)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgCostPerKwh = totalKwhForCost.compareTo(BigDecimal.ZERO) > 0
                ? energyCostEur.divide(totalKwhForCost, 2, RoundingMode.HALF_UP)
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
        // For time-filtered views: odometer-delta approach (trip-aware, boundary interpolation).
        // For all-time views: legacy chain-based approach via totalDistAll (conservative, avoids
        // counting odometer gaps from broken chains like TESLA_FLEET_IMPORT without odometer).
        BigDecimal totalDistanceKm = (startDate != null || endDate != null)
                ? computeOdometerDeltaDistance(carId, startDate, endDate)
                : (totalDistAll > 0 ? BigDecimal.valueOf(totalDistAll) : null);

        BigDecimal avgConsumptionKwhPer100km = ConsumptionMath.weightedAverage(totalWeighted, totalDist);

        // Fallback: if no SoC data available, use kWh/distance (distanceByLogId already computed above)
        if (avgConsumptionKwhPer100km == null) {
            List<EvLog> logsWithDistance = logs.stream()
                    .filter(l -> distanceByLogId.containsKey(l.getId()) && distanceByLogId.get(l.getId()) > 0)
                    .toList();
            if (!logsWithDistance.isEmpty()) {
                int totalDistInt = logsWithDistance.stream().mapToInt(l -> distanceByLogId.get(l.getId())).sum();
                if (totalDistanceKm == null) totalDistanceKm = BigDecimal.valueOf(totalDistInt);
                avgConsumptionKwhPer100km = calculationService.calculateConsumptionFallback(logsWithDistance, totalDistanceKm);
                estimatedCount = logsWithDistance.size();
            }
        }

        // Seasonal consumption — uses ALL logs (not time-filtered) for best signal
        SeasonalConsumptionResult seasonal = calculateSeasonalConsumption(List.of(car), isSeedUser);

        // Peer benchmark — only when car has a vehicle spec linked
        EvLogStatisticsResponse.PeerBenchmark peerBenchmark = null;
        if (car.getVehicleSpecificationId() != null) {
            peerBenchmark = buildPeerBenchmark(car, allLogsForCar, isSeedUser);
        }

        // Charging type split
        EvLogStatisticsResponse.ChargingTypeSplit chargingTypeSplit = buildChargingTypeSplit(logs);

        // Location split
        EvLogStatisticsResponse.LocationSplit locationSplit = buildLocationSplit(logs);

        // Charging efficiency split — only logs with both kwhCharged and kwhAtVehicle
        EvLogStatisticsResponse.ChargingEfficiencySplit efficiencySplit = buildChargingEfficiencySplit(logs);

        return new EvLogStatisticsResponse(
                totalKwhCharged,
                energyCostEur,
                fixedCosts.cost(),
                fixedCosts.income(),
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
                chargingTypeSplit,
                locationSplit,
                peerBenchmark,
                efficiencySplit
        );
    }

    private EvLogStatisticsResponse createEmptyStatisticsWithFixedCost(FixedCostTotals fixedCosts) {
        return createEmptyStatisticsWithFixedCostAndDistance(fixedCosts, null);
    }

    private EvLogStatisticsResponse createEmptyStatisticsWithFixedCostAndDistance(FixedCostTotals fixedCosts, BigDecimal distanceKm) {
        var emptyTypeSplit = new EvLogStatisticsResponse.ChargingTypeSplit(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var emptyLocSplit = new EvLogStatisticsResponse.LocationSplit(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var emptyEffSplit = new EvLogStatisticsResponse.ChargingEfficiencySplit(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        return new EvLogStatisticsResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, fixedCosts.cost(), fixedCosts.income(), fixedCosts.net(),
                BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                distanceKm, null, 0, null, null, List.of(), emptyTypeSplit, emptyLocSplit, null, emptyEffSplit
        );
    }

    private EvLogStatisticsResponse.PeerBenchmark buildPeerBenchmark(
            Car currentCar, List<EvLog> allLogsForCurrentCar, boolean isSeedUser) {

        // Primary match: same vehicleSpecificationId
        EvLogStatisticsResponse.PeerBenchmark.MatchType matchType = EvLogStatisticsResponse.PeerBenchmark.MatchType.SPEC;
        List<Car> peerCars = List.of();

        if (currentCar.getVehicleSpecificationId() != null) {
            List<Car> allCarsWithSpec = carRepository.findAllByVehicleSpecificationId(currentCar.getVehicleSpecificationId());
            peerCars = allCarsWithSpec.stream()
                    .filter(c -> !c.getUserId().equals(currentCar.getUserId()))
                    .toList();
        }

        // Fallback: same CarBrand.CarModel (all variants)
        if (peerCars.isEmpty()) {
            matchType = EvLogStatisticsResponse.PeerBenchmark.MatchType.MODEL;
            List<Car> allCarsWithModel = carRepository.findAllByModel(currentCar.getModel());
            peerCars = allCarsWithModel.stream()
                    .filter(c -> !c.getUserId().equals(currentCar.getUserId()))
                    .toList();
        }

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
            BigDecimal kwh = calculationService.gridSideKwhEstimate(log);
            if (kwh == null || kwh.compareTo(BigDecimal.ZERO) <= 0) continue;
            totalUserCost = totalUserCost.add(log.getCostEur());
            totalUserKwh = totalUserKwh.add(kwh);
        }
        if (totalUserKwh.compareTo(BigDecimal.ZERO) > 0) {
            userLifetimeCostPerKwh = totalUserCost.divide(totalUserKwh, 4, RoundingMode.HALF_UP);
        }

        // Peer cost — energy-weighted across all non-seed peers, regardless of country.
        // Country is unreliable (null for a large share of users) and filtering on it
        // shrinks the sample to the point where single outliers dominate the average.
        List<UUID> peerCarIds = nonSeedPeerCars.stream().map(Car::getId).toList();
        BigDecimal peerAvgCostPerKwh = null;
        BigDecimal totalPeerCost = BigDecimal.ZERO;
        BigDecimal totalPeerKwh = BigDecimal.ZERO;
        int peerLogCount = 0;
        for (EvLog log : evLogRepository.findAllByCarIds(peerCarIds)) {
            if (!log.isIncludeInStatistics()) continue;
            peerLogCount++;
            if (log.getCostEur() == null) continue;
            BigDecimal kwh = calculationService.gridSideKwhEstimate(log);
            if (kwh == null || kwh.compareTo(BigDecimal.ZERO) <= 0) continue;
            totalPeerCost = totalPeerCost.add(log.getCostEur());
            totalPeerKwh = totalPeerKwh.add(kwh);
        }
        if (totalPeerKwh.compareTo(BigDecimal.ZERO) > 0) {
            peerAvgCostPerKwh = totalPeerCost.divide(totalPeerKwh, 4, RoundingMode.HALF_UP);
        }

        long uniquePeerUsers = nonSeedPeerCars.stream().map(Car::getUserId).distinct().count();

        return new EvLogStatisticsResponse.PeerBenchmark(
                userLifetimeConsumption,
                peerConsumption.value(),
                userLifetimeCostPerKwh,
                peerAvgCostPerKwh,
                (int) uniquePeerUsers,
                peerConsumption.tripCount(),
                peerLogCount,
                matchType
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

                    // totalKwh ueber gridSideKwhEstimate: faellt auf kwh_at_vehicle/efficiency
                    // zurueck, wenn kwh_charged fehlt (typisch Smartcar/Tesla-Telemetry-Logs).
                    BigDecimal totalKwh = periodLogs.stream()
                            .map(calculationService::gridSideKwhEstimate)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // totalCost: NULL wenn alle Logs cost_eur=NULL haben (kein Preis erfasst).
                    // 0 bleibt eine echte Aussage (Solar/Eigenstrom).
                    long nonNullCostCount = periodLogs.stream()
                            .map(EvLog::getCostEur)
                            .filter(Objects::nonNull)
                            .count();
                    BigDecimal totalCost = nonNullCostCount > 0
                            ? periodLogs.stream()
                                    .map(EvLog::getCostEur)
                                    .filter(Objects::nonNull)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                            : null;

                    // Distance and consumption: SoC-based, plausible logs only
                    BigDecimal periodWeighted = BigDecimal.ZERO;
                    int periodDist = 0;
                    for (EvLog log : periodLogs) {
                        ConsumptionResult cr = consumptionByLog.get(log.getId());
                        if (cr == null) continue;
                        periodWeighted = periodWeighted.add(cr.value().multiply(BigDecimal.valueOf(cr.distanceKm())));
                        periodDist += cr.distanceKm();
                    }

                    // periodDist=0 = Periode hatte zwar Logs, aber keine plausible Strecke
                    // (z.B. nur Heim-Wallbox ohne Odometer-Bewegung). 0 statt null heisst:
                    // Chart zeichnet einen "0 km gefahren"-Punkt statt einer Luecke.
                    BigDecimal periodDistance = BigDecimal.valueOf(periodDist);
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

    /**
     * The price the log form pre-fills. Same rule and same numbers the backend would store on save
     * - {@link LocationPricing} is the single source, so suggestion and stored value cannot drift.
     */
    public Optional<PriceSuggestion> getPriceSuggestion(UUID userId, double latitude, double longitude,
                                                        boolean isPublicCharging, ChargingType chargingType) {
        int precision = isPublicCharging ? 7 : 6;
        String geohash = GeoHash.withCharacterPrecision(latitude, longitude, precision).toBase32();
        return locationPricing.tariffAt(userId, geohash, chargingType, isPublicCharging)
                .map(tariff -> new PriceSuggestion(tariff.pricePerKwh(), tariff.chargingProviderId()));
    }

    private EvLogStatisticsResponse.ChargingTypeSplit buildChargingTypeSplit(List<EvLog> logs) {
        BigDecimal ac = BigDecimal.ZERO;
        BigDecimal dc = BigDecimal.ZERO;
        BigDecimal unknown = BigDecimal.ZERO;
        for (EvLog log : logs) {
            BigDecimal kwh = log.getKwhCharged() != null ? log.getKwhCharged() : log.getKwhAtVehicle();
            if (kwh == null) continue;
            ChargingType type = log.getChargingType() != null ? log.getChargingType() : ChargingType.UNKNOWN;
            switch (type) {
                case AC -> ac = ac.add(kwh);
                case DC -> dc = dc.add(kwh);
                default -> unknown = unknown.add(kwh);
            }
        }
        return new EvLogStatisticsResponse.ChargingTypeSplit(ac, dc, unknown);
    }

    /**
     * Drei Toepfe statt zwei: ein Log ohne Angabe zum Ladeort landet nicht mehr still
     * bei den Heimladungen. Automatisch erzeugte Logs tragen den Ort haeufig nicht -
     * Tesla-Telemetrie etwa meldet nur DC sicher, AC bleibt bewusst offen.
     */
    private EvLogStatisticsResponse.LocationSplit buildLocationSplit(List<EvLog> logs) {
        BigDecimal publicKwh = BigDecimal.ZERO;
        BigDecimal privateKwh = BigDecimal.ZERO;
        BigDecimal unknownKwh = BigDecimal.ZERO;
        for (EvLog log : logs) {
            BigDecimal kwh = log.getKwhCharged() != null ? log.getKwhCharged() : log.getKwhAtVehicle();
            if (kwh == null) continue;
            if (log.isPublicChargingConfirmed()) {
                publicKwh = publicKwh.add(kwh);
            } else if (log.isHomeChargingConfirmed()) {
                privateKwh = privateKwh.add(kwh);
            } else {
                unknownKwh = unknownKwh.add(kwh);
            }
        }
        return new EvLogStatisticsResponse.LocationSplit(publicKwh, privateKwh, unknownKwh);
    }

    private EvLogStatisticsResponse.ChargingEfficiencySplit buildChargingEfficiencySplit(List<EvLog> logs) {
        BigDecimal gridKwh = BigDecimal.ZERO;
        BigDecimal vehicleKwh = BigDecimal.ZERO;
        int covered = 0;
        for (EvLog log : logs) {
            if (log.getKwhCharged() != null && log.getKwhAtVehicle() != null) {
                gridKwh = gridKwh.add(log.getKwhCharged());
                vehicleKwh = vehicleKwh.add(log.getKwhAtVehicle());
                covered++;
            }
        }
        return new EvLogStatisticsResponse.ChargingEfficiencySplit(gridKwh, vehicleKwh, covered, logs.size());
    }

    public PeerModelComparisonResponse getPeerModelComparison(Car userCar, User user) {
        if (userCar.getVehicleSpecificationId() == null) {
            return new PeerModelComparisonResponse(List.of());
        }

        VehicleSpecification userSpec = vehicleSpecificationRepository.findById(userCar.getVehicleSpecificationId())
                .orElseThrow(() -> new IllegalArgumentException("Vehicle spec not found"));

        // 1. Hol alle specs mit dem gleichen car_model
        List<VehicleSpecification> allSpecsOfModel = vehicleSpecificationRepository
                .findByCarModelOrderByBatteryCapacityKwhAsc(userSpec.getCarModel());

        List<PeerModelComparisonItem> items = new ArrayList<>();

        // 2. Für jede spec: aggregiere logs
        for (VehicleSpecification spec : allSpecsOfModel) {
            // Alle cars mit dieser spec (außer des Users)
            List<Car> peerCars = carRepository.findAllByVehicleSpecificationId(spec.getId()).stream()
                    .filter(c -> !c.getUserId().equals(user.getId()))
                    .toList();

            // Peer-Metriken
            BigDecimal peerConsumption = null;
            BigDecimal peerCostPerKwh = null;
            int peerCarCount = 0;

            if (!peerCars.isEmpty()) {
                CommunityConsumptionResult consumption = calculateCommunityAvgConsumption(peerCars, false);
                peerConsumption = consumption.value();

                // Cost-Berechnung (analog zu buildPeerBenchmark)
                peerCostPerKwh = calculateAvgCostPerKwhForCars(peerCars);
                peerCarCount = peerCars.size();
            }

            // Skip specs with no peer data at all (unless it's the user's spec)
            boolean isUserSpec = spec.getId().equals(userSpec.getId());
            if (!isUserSpec && peerConsumption == null && peerCostPerKwh == null) {
                continue;
            }

            // User-Metriken (nur wenn es die user spec ist)
            UserMetrics userMetrics = null;
            if (spec.getId().equals(userSpec.getId())) {
                // Hol alle logs des users für dieses auto
                List<EvLog> userLogs = evLogRepository.findAllByCarId(userCar.getId());
                List<EvLog> statsLogs = userLogs.stream()
                        .filter(l -> l.isIncludeInStatistics())
                        .toList();

                BigDecimal userConsumption = null;
                if (!statsLogs.isEmpty()) {
                    List<PlausibleEntry> entries = getPlausibleEntriesForCar(userCar, userLogs, statsLogs);
                    if (!entries.isEmpty()) {
                        BigDecimal weighted = BigDecimal.ZERO;
                        int totalDist = 0;
                        for (PlausibleEntry e : entries) {
                            weighted = weighted.add(e.consumptionKwhPer100km().multiply(BigDecimal.valueOf(e.distanceKm())));
                            totalDist += e.distanceKm();
                        }
                        userConsumption = ConsumptionMath.weightedAverage(weighted, totalDist);
                    }
                }

                BigDecimal userCostPerKwh = null;
                BigDecimal totalCost = BigDecimal.ZERO;
                BigDecimal totalKwh = BigDecimal.ZERO;
                for (EvLog log : statsLogs) {
                    if (log.getCostEur() == null) continue;
                    BigDecimal kwh = calculationService.gridSideKwhEstimate(log);
                    if (kwh == null || kwh.compareTo(BigDecimal.ZERO) <= 0) continue;
                    totalCost = totalCost.add(log.getCostEur());
                    totalKwh = totalKwh.add(kwh);
                }
                if (totalKwh.compareTo(BigDecimal.ZERO) > 0) {
                    userCostPerKwh = totalCost.divide(totalKwh, 4, RoundingMode.HALF_UP);
                }

                userMetrics = new UserMetrics(userConsumption, userCostPerKwh);
            }

            // Add to results (already filtered for empty specs above)
            items.add(new PeerModelComparisonItem(
                    spec.getId(),
                    spec.getVariantName() + " (" + spec.getBatteryCapacityKwh() + "kWh)",
                    isUserSpec,
                    spec.getNetBatteryCapacityKwh(),
                    userMetrics,
                    new PeerMetrics(peerConsumption, peerCostPerKwh, peerCarCount)
            ));
        }

        return new PeerModelComparisonResponse(items);
    }

    private BigDecimal calculateAvgCostPerKwhForCars(List<Car> cars) {
        List<UUID> carIds = cars.stream().map(Car::getId).toList();
        List<EvLog> allLogs = evLogRepository.findAllByCarIds(carIds);

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalKwh = BigDecimal.ZERO;

        for (EvLog log : allLogs) {
            if (!log.isIncludeInStatistics()) continue;
            if (log.getCostEur() == null) continue;
            BigDecimal kwh = calculationService.gridSideKwhEstimate(log);
            if (kwh == null || kwh.compareTo(BigDecimal.ZERO) <= 0) continue;
            totalCost = totalCost.add(log.getCostEur());
            totalKwh = totalKwh.add(kwh);
        }

        if (totalKwh.compareTo(BigDecimal.ZERO) > 0) {
            return totalCost.divide(totalKwh, 4, RoundingMode.HALF_UP);
        }

        return null;
    }

    /**
     * Odometer-delta based distance for a time period.
     *
     * Strategy:
     *   1. Collect all odometer-bearing datapoints from ev_log and ev_trip for the car,
     *      sorted by timestamp.
     *   2. Kern: delta between oldest and newest datapoint inside [from, to].
     *   3. Lower boundary interpolation: if a predecessor exists before `from`, interpolate
     *      the fraction of the predecessor→oldest-in-period delta that falls within the period.
     *   4. Upper boundary interpolation: same logic for the period end.
     *
     * Returns null if no odometer-bearing datapoint exists inside the period.
     * If from/to is null (all-time view), returns the raw delta across all datapoints.
     */
    BigDecimal computeOdometerDeltaDistance(UUID carId, LocalDate from, LocalDate to) {
        // Build unified list of (timestamp, odometerKm) from ev_log + ev_trip
        record OdoPoint(OffsetDateTime ts, BigDecimal odo) {}

        List<OdoPoint> points = new ArrayList<>();

        evLogRepository.findAllByCarId(carId).stream()
                .filter(l -> l.getOdometerKm() != null)
                .forEach(l -> points.add(new OdoPoint(
                        l.getLoggedAt().atOffset(ZoneOffset.UTC), BigDecimal.valueOf(l.getOdometerKm()))));

        evTripRepository.findAllByCarIdAndDeletedAtIsNull(carId)
                .forEach(t -> {
                    if (t.getOdometerEndKm() != null && t.getTripEndedAt() != null) {
                        points.add(new OdoPoint(t.getTripEndedAt(), t.getOdometerEndKm()));
                    } else if (t.getOdometerStartKm() != null) {
                        points.add(new OdoPoint(t.getTripStartedAt(), t.getOdometerStartKm()));
                    }
                });

        points.sort(Comparator.comparing(OdoPoint::ts));

        if (points.isEmpty()) return null;

        OffsetDateTime periodStart = from != null ? from.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime periodEnd   = to   != null ? to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) : null;

        // No period filter → simple all-time delta
        if (periodStart == null && periodEnd == null) {
            return points.getLast().odo().subtract(points.getFirst().odo()).max(BigDecimal.ZERO);
        }

        List<OdoPoint> inPeriod = points.stream()
                .filter(p -> (periodStart == null || !p.ts().isBefore(periodStart))
                          && (periodEnd   == null || p.ts().isBefore(periodEnd)))
                .toList();

        if (inPeriod.isEmpty()) return null;

        // Kern: delta between oldest and newest inside the period
        BigDecimal kern = inPeriod.getLast().odo().subtract(inPeriod.getFirst().odo()).max(BigDecimal.ZERO);

        // Lower boundary interpolation
        BigDecimal lowerInterp = BigDecimal.ZERO;
        if (periodStart != null) {
            OdoPoint oldest = inPeriod.getFirst();
            // Find the latest point strictly before periodStart
            OdoPoint predecessor = null;
            for (int i = points.size() - 1; i >= 0; i--) {
                if (points.get(i).ts().isBefore(periodStart)) { predecessor = points.get(i); break; }
            }
            if (predecessor != null && oldest.odo().compareTo(predecessor.odo()) != 0) {
                long totalDays = java.time.temporal.ChronoUnit.DAYS.between(predecessor.ts(), oldest.ts());
                long gapDays   = java.time.temporal.ChronoUnit.DAYS.between(periodStart, oldest.ts());
                if (totalDays > 0 && gapDays > 0) {
                    BigDecimal segDelta = oldest.odo().subtract(predecessor.odo()).max(BigDecimal.ZERO);
                    lowerInterp = segDelta
                            .multiply(BigDecimal.valueOf(gapDays))
                            .divide(BigDecimal.valueOf(totalDays), 3, RoundingMode.HALF_UP);
                }
            }
        }

        // Upper boundary interpolation
        BigDecimal upperInterp = BigDecimal.ZERO;
        if (periodEnd != null) {
            OdoPoint newest = inPeriod.getLast();
            // Find the earliest point strictly after periodEnd
            OdoPoint successor = null;
            for (OdoPoint p : points) {
                if (!p.ts().isBefore(periodEnd)) { successor = p; break; }
            }
            if (successor != null && successor.odo().compareTo(newest.odo()) != 0) {
                long totalDays = java.time.temporal.ChronoUnit.DAYS.between(newest.ts(), successor.ts());
                long gapDays   = java.time.temporal.ChronoUnit.DAYS.between(newest.ts(), periodEnd);
                if (totalDays > 0 && gapDays > 0) {
                    BigDecimal segDelta = successor.odo().subtract(newest.odo()).max(BigDecimal.ZERO);
                    upperInterp = segDelta
                            .multiply(BigDecimal.valueOf(gapDays))
                            .divide(BigDecimal.valueOf(totalDays), 3, RoundingMode.HALF_UP);
                }
            }
        }

        return kern.add(lowerInterp).add(upperInterp).setScale(1, RoundingMode.HALF_UP);
    }
}
