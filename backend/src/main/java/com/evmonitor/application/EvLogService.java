package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.weather.TemperatureEnrichmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvLogService {

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final CoinLogService coinLogService;
    private final TemperatureEnrichmentService temperatureEnrichmentService;
    private final VehicleSpecificationRepository vehicleSpecificationRepository;
    private final PlausibilityProperties plausibility;

    public EvLogService(EvLogRepository evLogRepository, CarRepository carRepository, UserRepository userRepository,
            CoinLogService coinLogService, TemperatureEnrichmentService temperatureEnrichmentService,
            VehicleSpecificationRepository vehicleSpecificationRepository, PlausibilityProperties plausibility) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.coinLogService = coinLogService;
        this.temperatureEnrichmentService = temperatureEnrichmentService;
        this.vehicleSpecificationRepository = vehicleSpecificationRepository;
        this.plausibility = plausibility;
    }

    @Transactional
    public EvLogCreateResponse logCharging(UUID userId, EvLogRequest request) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Convert lat/lon to 5-character geohash for privacy (~5km precision)
        // Lat/lon are never stored, only the anonymized geohash
        String geohash = null;
        if (request.latitude() != null && request.longitude() != null) {
            geohash = GeoHash.withCharacterPrecision(request.latitude(), request.longitude(), 5).toBase32();
        }

        EvLog newLog = EvLog.createNew(
                request.carId(),
                request.kwhCharged(),
                request.costEur(),
                request.chargeDurationMinutes(),
                geohash,
                request.odometerKm(),
                request.maxChargingPowerKw(),
                request.socAfterChargePercent(),
                request.loggedAt());

        EvLog savedLog = evLogRepository.save(newLog);

        // Async: enrich with temperature from Open-Meteo (fire-and-forget, nullable result)
        temperatureEnrichmentService.enrichLog(savedLog.getId(), savedLog.getGeohash(), savedLog.getLoggedAt());

        // Award coins: 25 for first log ever, 5 for each subsequent one; +2 if OCR was used.
        // Check coin history instead of current log count to prevent delete-and-recreate farming.
        // NOTE: ocrUsed is client-supplied and not server-verifiable — the +2 bonus is accepted risk
        // (low value, requires conscious manipulation, not worth server-side OCR session tracking).
        boolean firstLogEver = !coinLogService.hasEverReceivedCoinForAction(
                userId, CoinLogService.ACTION_LOG_CREATED);
        int coins = firstLogEver ? 25 : 5;
        if (Boolean.TRUE.equals(request.ocrUsed())) {
            coins += 2;
        }
        coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, coins, CoinLogService.ACTION_LOG_CREATED);

        return new EvLogCreateResponse(EvLogResponse.fromDomain(savedLog), coins);
    }

    /**
     * Creates a charging log on behalf of a user from an OCPP wallbox session.
     * Called by the internal Wallbox Service — not user-facing.
     */
    @Transactional
    public EvLogResponse createWallboxLog(InternalEvLogRequest request) {
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(request.userId())) {
            throw new IllegalArgumentException("Car does not belong to user");
        }

        DataSource source = DataSource.WALLBOX_OCPP;
        if (request.dataSource() != null) {
            try { source = DataSource.valueOf(request.dataSource()); } catch (IllegalArgumentException ignored) {}
        }

        // Idempotent: skip if already imported (same car + timestamp + data source)
        if (evLogRepository.existsByCarIdAndLoggedAtAndDataSource(request.carId(), request.loggedAt(), source)) {
            return null;
        }

        EvLog newLog = EvLog.createFromInternal(
                request.carId(),
                request.kwhCharged(),
                request.chargeDurationMinutes(),
                request.geohash(),
                request.loggedAt(),
                request.odometerSuggestionMinKm(),
                request.odometerSuggestionMaxKm(),
                source,
                request.costEur());

        EvLog savedLog = evLogRepository.save(newLog);
        return EvLogResponse.fromDomain(savedLog);
    }

    @Transactional
    public boolean updateGeohash(UUID carId, UUID userId, LocalDateTime loggedAt, String geohash) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Car does not belong to user");
        }
        return evLogRepository.updateGeohash(carId, loggedAt, geohash);
    }

    public List<EvLogResponse> getAllLogsForUser(UUID userId) {
        List<Car> cars = carRepository.findAllByUserId(userId);
        List<EvLogResponse> allLogs = new ArrayList<>();

        for (Car car : cars) {
            List<EvLogResponse> logsForCar = evLogRepository.findAllByCarId(car.getId())
                    .stream()
                    .map(EvLogResponse::fromDomain)
                    .toList();
            allLogs.addAll(logsForCar);
        }

        return allLogs;
    }

    public EvLogResponse getLogByIdForUser(UUID id, UUID userId) {
        EvLog log = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + id));

        Car car = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        return EvLogResponse.fromDomain(log);
    }

    public void deleteLog(UUID id, UUID userId) {
        EvLog log = evLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Log not found with ID: " + id));

        Car car = carRepository.findById(log.getCarId())
                .orElseThrow(() -> new IllegalArgumentException("Associated car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Log not found for current user (ownership mismatch).");
        }

        evLogRepository.deleteById(id);
    }

    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId) {
        return getLogsForCar(carId, userId, null);
    }

    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId, Integer limit) {
        return getLogsForCar(carId, userId, limit, 0);
    }

    public List<EvLogResponse> getLogsForCar(UUID carId, UUID userId, Integer limit, int page) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // All logs sorted ascending — needed for consumption context (logX lookups)
        List<EvLog> allLogsSorted = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.toList());

        // Compute per-log consumption + plausibility on the full dataset
        Map<UUID, ConsumptionResult> consumptionByLog = car.getBatteryCapacityKwh() != null
                ? calculateConsumptionPerLog(allLogsSorted, car.getBatteryCapacityKwh(), lookupWltp(car))
                : Map.of();

        // Return the requested page, enriched with consumption data
        List<EvLog> page_logs = (limit != null && limit > 0)
                ? evLogRepository.findLatestByCarId(carId, limit, page)
                : allLogsSorted.reversed();

        return page_logs.stream()
                .map(log -> EvLogResponse.fromDomain(log, consumptionByLog.get(log.getId())))
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
    public EvLogStatisticsResponse getStatistics(UUID carId, UUID userId,
            java.time.LocalDate startDate, java.time.LocalDate endDate, String groupBy) {
        // Verify ownership
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Check if user is a seed user (for demo mode)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        boolean isSeedUser = user.isSeedData();

        // Get all logs for this car
        // Demo Mode: Seed users see ALL their logs (including their own seed data)
        // Regular users: Only see logs marked for statistics
        List<EvLog> logs = evLogRepository.findAllByCarId(carId).stream()
                .filter(log -> isSeedUser || log.isIncludeInStatistics())
                .collect(Collectors.toList());

        // Apply time filter
        if (startDate != null || endDate != null) {
            logs = logs.stream()
                    .filter(log -> {
                        java.time.LocalDate logDate = log.getLoggedAt().toLocalDate();
                        boolean afterStart = startDate == null || !logDate.isBefore(startDate);
                        boolean beforeEnd = endDate == null || !logDate.isAfter(endDate);
                        return afterStart && beforeEnd;
                    })
                    .collect(Collectors.toList());
        }

        if (logs.isEmpty()) {
            return createEmptyStatistics();
        }

        // Calculate key metrics
        BigDecimal totalKwhCharged = logs.stream()
                .map(EvLog::getKwhCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCostEur = logs.stream()
                .map(EvLog::getCostEur)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgCostPerKwh = totalKwhCharged.compareTo(BigDecimal.ZERO) > 0
                ? totalCostEur.divide(totalKwhCharged, 2, RoundingMode.HALF_UP)
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

        // Compute per-log distance from consecutive odometer readings (all logs for this car, sorted)
        List<EvLog> allLogsForCar = evLogRepository.findAllByCarId(carId).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.toList());
        Map<UUID, Integer> distanceByLogId = computeDistanceByLogId(allLogsForCar);

        // Charge over time data (grouped and sorted by time)
        List<EvLogStatisticsResponse.ChargeDataPoint> chargesOverTime =
                groupChargesByPeriod(logs, groupBy != null ? groupBy : "MONTH", distanceByLogId);

        // Total distance and avg consumption (using SoC-based calculation if available)
        BigDecimal totalDistanceKm = null;
        BigDecimal avgConsumptionKwhPer100km = null;
        List<EvLog> filteredLogsWithDistance = logs.stream()
                .filter(l -> distanceByLogId.containsKey(l.getId()) && distanceByLogId.get(l.getId()) > 0)
                .collect(Collectors.toList());
        if (!filteredLogsWithDistance.isEmpty()) {
            int totalDistanceInt = filteredLogsWithDistance.stream()
                    .mapToInt(l -> distanceByLogId.get(l.getId())).sum();
            totalDistanceKm = new BigDecimal(totalDistanceInt);

            // Try SoC-based calculation first (more accurate for partial charging)
            avgConsumptionKwhPer100km = calculateConsumptionWithSoc(allLogsForCar, filteredLogsWithDistance, car.getBatteryCapacityKwh(), lookupWltp(car));

            // Fallback to kWh-based calculation if SoC data is missing
            if (avgConsumptionKwhPer100km == null) {
                avgConsumptionKwhPer100km = calculateConsumptionFallback(filteredLogsWithDistance, totalDistanceKm);
            }
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
                chargesOverTime
        );
    }

    private EvLogStatisticsResponse createEmptyStatistics() {
        return new EvLogStatisticsResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                null, null, List.of()
        );
    }

    /**
     * Calculates consumption (kWh/100km) for the trip from logX to logY.
     *
     * logX (trip start): logX.canBeUsedAsLogX() must be true
     *                    requires: odometer + socAfterChargePercent
     * logY (trip end):   logY.isComplete() must be true
     *                    requires: odometer + kwhCharged + socAfterChargePercent
     *
     * Formula:
     *   socBefore(logY)  = socAfter(logY) - kwhCharged(logY) / batteryCapacity * 100
     *   energyConsumed   = (socAfter(logX) - socBefore(logY)) * batteryCapacity / 100
     *   consumption      = energyConsumed / distance * 100
     *
     * Package-private for unit testing.
     *
     * @return consumption in kWh/100km, or empty if data is insufficient or result is invalid
     */
    Optional<BigDecimal> calculateConsumption(EvLog logX, EvLog logY, BigDecimal batteryCapacityKwh) {
        if (!logX.canBeUsedAsLogX() || !logY.isComplete()) return Optional.empty();
        if (batteryCapacityKwh == null || batteryCapacityKwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        int distance = logY.getOdometerKm() - logX.getOdometerKm();
        if (distance <= 0) return Optional.empty();

        // socBefore(logY) = socAfter(logY) - kwhCharged(logY) / batteryCapacity * 100
        BigDecimal socBeforeLogYPercent = new BigDecimal(logY.getSocAfterChargePercent())
                .subtract(logY.getKwhCharged()
                        .divide(batteryCapacityKwh, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")));

        // energyConsumed = (socAfter(logX) - socBefore(logY)) * batteryCapacity / 100
        BigDecimal energyConsumedKwh = new BigDecimal(logX.getSocAfterChargePercent())
                .subtract(socBeforeLogYPercent)
                .multiply(batteryCapacityKwh)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        if (energyConsumedKwh.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        return Optional.of(energyConsumedKwh
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal(distance), 2, RoundingMode.HALF_UP));
    }

    /**
     * Calculates average consumption across all valid trips using the SoC-based method.
     * Delegates to calculateConsumptionPerLog() for raw values + plausibility verdicts.
     * Only plausible trips contribute to the distance-weighted average.
     *
     * @return avg consumption in kWh/100km, or null if no plausible trips found
     */
    private BigDecimal calculateConsumptionWithSoc(List<EvLog> allLogs, List<EvLog> logsWithDistance, BigDecimal batteryCapacityKwh, BigDecimal wltpKwh) {
        Map<UUID, ConsumptionResult> perLog = calculateConsumptionPerLog(allLogs, batteryCapacityKwh, wltpKwh);

        BigDecimal totalWeightedConsumption = BigDecimal.ZERO;
        int totalDistance = 0;

        for (EvLog logY : logsWithDistance) {
            ConsumptionResult cr = perLog.get(logY.getId());
            if (cr == null || !cr.plausible()) continue;

            totalWeightedConsumption = totalWeightedConsumption
                    .add(cr.value().multiply(new BigDecimal(cr.distanceKm())));
            totalDistance += cr.distanceKm();
        }

        if (totalDistance == 0) return null;

        return totalWeightedConsumption
                .divide(new BigDecimal(totalDistance), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates per-log consumption (kWh/100km) for each complete log, with plausibility verdict.
     *
     * Two-pass algorithm:
     *   Pass 1 — compute raw consumption for every complete log (isComplete + previous log with odometer).
     *            Trips shorter than minTripDistanceKm are excluded (unreliable odometer data).
     *   Pass 2 — check each value against the full distribution + WLTP reference via isConsumptionPlausible().
     *
     * @return map of logId (logY) → ConsumptionResult(value, plausible, distanceKm)
     */
    Map<UUID, ConsumptionResult> calculateConsumptionPerLog(List<EvLog> allLogs, BigDecimal batteryCapacityKwh, BigDecimal wltpKwh) {
        // Always sort — correctness must not depend on caller discipline
        List<EvLog> sorted = allLogs.stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .collect(Collectors.toList());

        // Pass 1: raw consumptions
        List<UUID> ids = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<Integer> distances = new ArrayList<>();

        sorted.stream()
                .filter(EvLog::isComplete)
                .forEach(logY -> {
                    EvLog logX = findPreviousLog(sorted, logY);
                    if (logX == null) return;
                    int dist = logY.getOdometerKm() - logX.getOdometerKm();
                    if (dist < plausibility.getMinTripDistanceKm()) return;
                    calculateConsumption(logX, logY, batteryCapacityKwh).ifPresent(c -> {
                        ids.add(logY.getId());
                        values.add(c);
                        distances.add(dist);
                    });
                });

        // Pass 2: plausibility check — absolute bounds + statistical/WLTP reference
        Map<UUID, ConsumptionResult> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            boolean plausible = isConsumptionPlausible(values.get(i), values, wltpKwh);
            result.put(ids.get(i), new ConsumptionResult(values.get(i), plausible, distances.get(i)));
        }
        return result;
    }

    /**
     * Checks whether a calculated consumption value is plausible for this car.
     *
     * Three layers:
     *   Layer 1 — Absolute sanity bounds: [absoluteMin, absoluteMax] kWh/100km (always applied).
     *   Layer 2a — Statistical check (≥ minTripsForStatistical trips):
     *              mean(history) ± sigmaMultiplier × stdDev(history).
     *   Layer 2b — WLTP bootstrap (< minTripsForStatistical, WLTP available):
     *              [WLTP × wltpLowerFactor, WLTP × wltpUpperFactor].
     *   Layer 2c — Only absolute bounds apply (no history, no WLTP).
     *
     * Implausible values likely indicate a missing charging session between logX and logY.
     *
     * @param consumptionKwhPer100km     the calculated value to check
     * @param historicalConsumptions     all computed consumptions for this car (may include self)
     * @param wltpConsumptionKwhPer100km the car's WLTP reference value (nullable)
     */
    boolean isConsumptionPlausible(BigDecimal consumptionKwhPer100km,
                                   List<BigDecimal> historicalConsumptions,
                                   BigDecimal wltpConsumptionKwhPer100km) {
        // Layer 1: absolute sanity bounds
        BigDecimal absMin = BigDecimal.valueOf(plausibility.getAbsoluteMinKwhPer100km());
        BigDecimal absMax = BigDecimal.valueOf(plausibility.getAbsoluteMaxKwhPer100km());
        if (consumptionKwhPer100km.compareTo(absMin) < 0) return false;
        if (consumptionKwhPer100km.compareTo(absMax) > 0) return false;

        List<BigDecimal> history = historicalConsumptions != null ? historicalConsumptions : List.of();

        // Layer 2a: statistical check
        if (history.size() >= plausibility.getMinTripsForStatistical()) {
            BigDecimal mean = computeMean(history);
            BigDecimal stdDev = computeStdDev(history, mean);
            if (stdDev.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margin = stdDev.multiply(BigDecimal.valueOf(plausibility.getSigmaMultiplier()));
                return consumptionKwhPer100km.compareTo(mean.subtract(margin)) >= 0
                        && consumptionKwhPer100km.compareTo(mean.add(margin)) <= 0;
            }
            // stdDev == 0: all values identical — accept only if within 10% of mean
            BigDecimal tolerance = mean.multiply(new BigDecimal("0.10"));
            return consumptionKwhPer100km.subtract(mean).abs().compareTo(tolerance) <= 0;
        }

        // Layer 2b: WLTP bootstrap
        if (wltpConsumptionKwhPer100km != null && wltpConsumptionKwhPer100km.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lower = wltpConsumptionKwhPer100km.multiply(BigDecimal.valueOf(plausibility.getWltpLowerFactor()));
            BigDecimal upper = wltpConsumptionKwhPer100km.multiply(BigDecimal.valueOf(plausibility.getWltpUpperFactor()));
            return consumptionKwhPer100km.compareTo(lower) >= 0
                    && consumptionKwhPer100km.compareTo(upper) <= 0;
        }

        // Layer 2c: only absolute bounds (already passed above)
        return true;
    }

    private BigDecimal computeMean(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(values.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal computeStdDev(List<BigDecimal> values, BigDecimal mean) {
        BigDecimal sumSquares = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = sumSquares.divide(new BigDecimal(values.size()), 10, RoundingMode.HALF_UP);
        return variance.sqrt(new MathContext(10, RoundingMode.HALF_UP));
    }

    /**
     * Fallback consumption calculation when SoC data is missing.
     * Simple formula: (total kWh charged / total distance) × 100
     * Less accurate for partial charging but works when SoC is unavailable.
     * Package-private for testing.
     */
    BigDecimal calculateConsumptionFallback(List<EvLog> logs, BigDecimal totalDistanceKm) {
        if (totalDistanceKm == null || totalDistanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal totalKwh = logs.stream()
            .map(EvLog::getKwhCharged)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalKwh
            .multiply(new BigDecimal("100"))
            .divide(totalDistanceKm, 2, RoundingMode.HALF_UP);
    }

    /**
     * Looks up the WLTP combined consumption for the given car via VehicleSpecification.
     * Returns null if no spec exists (e.g. model not yet in the database).
     */
    BigDecimal lookupWltp(Car car) {
        if (car.getModel() == null || car.getBatteryCapacityKwh() == null) return null;
        return vehicleSpecificationRepository
                .findByCarBrandAndModelAndCapacityAndType(
                        car.getModel().getBrand().name(),
                        car.getModel().name(),
                        car.getBatteryCapacityKwh(),
                        VehicleSpecification.WltpType.COMBINED)
                .map(VehicleSpecification::getWltpConsumptionKwhPer100km)
                .orElse(null);
    }

    /**
     * Returns the immediately preceding log (by loggedAt) as a candidate for logX.
     * Returns null if the directly previous log does not satisfy canBeUsedAsLogX().
     *
     * Strict-by-design: any log between logX and logY — even an incomplete one —
     * represents an unknown energy event. Skipping over it would silently corrupt
     * the SoC-delta calculation. If the direct predecessor isn't usable, the trip
     * cannot be calculated at all.
     *
     * Callers must pass a list sorted ascending by loggedAt.
     * calculateConsumptionPerLog() guarantees this internally.
     */
    private EvLog findPreviousLog(List<EvLog> sortedLogs, EvLog logY) {
        for (int i = 0; i < sortedLogs.size(); i++) {
            if (sortedLogs.get(i).getId().equals(logY.getId())) {
                if (i == 0) return null;
                EvLog candidate = sortedLogs.get(i - 1);
                return candidate.canBeUsedAsLogX() ? candidate : null;
            }
        }
        return null;
    }

    /**
     * Compute distance driven before each charge from consecutive odometer readings.
     * Distance for log[i] = log[i].odometer - log[j].odometer where j is the most recent log with odometer data.
     * Skips logs without odometer data to find the actual previous trip.
     * Logs must be sorted by loggedAt ascending.
     */
    private Map<UUID, Integer> computeDistanceByLogId(List<EvLog> sortedLogs) {
        Map<UUID, Integer> result = new java.util.HashMap<>();
        for (int i = 1; i < sortedLogs.size(); i++) {
            EvLog current = sortedLogs.get(i);
            if (current.getOdometerKm() == null) {
                continue; // Skip logs without odometer
            }

            // Find previous log with odometer data (search backwards)
            EvLog previous = null;
            for (int j = i - 1; j >= 0; j--) {
                if (sortedLogs.get(j).getOdometerKm() != null) {
                    previous = sortedLogs.get(j);
                    break;
                }
            }

            if (previous != null) {
                int distance = current.getOdometerKm() - previous.getOdometerKm();
                if (distance > 0) {
                    result.put(current.getId(), distance);
                }
            }
        }
        return result;
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
    public BigDecimal calculateCommunityAvgConsumption(List<Car> cars, boolean isSeedUser) {
        BigDecimal totalWeightedConsumption = BigDecimal.ZERO;
        int totalDistance = 0;

        for (Car car : cars) {
            List<EvLog> allLogs = evLogRepository.findAllByCarId(car.getId()).stream()
                    .sorted(Comparator.comparing(EvLog::getLoggedAt))
                    .collect(Collectors.toList());

            List<EvLog> statsLogs = allLogs.stream()
                    .filter(l -> isSeedUser || l.isIncludeInStatistics())
                    .toList();

            if (statsLogs.isEmpty()) continue;

            Map<UUID, Integer> distanceByLogId = computeDistanceByLogId(allLogs);

            List<EvLog> logsWithDistance = statsLogs.stream()
                    .filter(l -> distanceByLogId.containsKey(l.getId()))
                    .collect(Collectors.toList());

            if (logsWithDistance.isEmpty()) continue;

            int carDistance = logsWithDistance.stream()
                    .mapToInt(l -> distanceByLogId.get(l.getId())).sum();

            // SoC-based calculation (most accurate, requires socAfterChargePercent)
            BigDecimal carConsumption = calculateConsumptionWithSoc(
                    allLogs, logsWithDistance, car.getBatteryCapacityKwh(), lookupWltp(car));

            // Fallback: simple kWh/distance, but filter implausible trips first
            if (carConsumption == null) {
                List<EvLog> plausibleLogs = logsWithDistance.stream()
                        .filter(l -> {
                            int dist = distanceByLogId.get(l.getId());
                            double consumption = l.getKwhCharged().doubleValue() / dist * 100;
                            return consumption >= plausibility.getAbsoluteMinKwhPer100km()
                                    && consumption <= plausibility.getAbsoluteMaxKwhPer100km();
                        })
                        .collect(Collectors.toList());

                if (plausibleLogs.isEmpty()) continue;

                int plausibleDistance = plausibleLogs.stream()
                        .mapToInt(l -> distanceByLogId.get(l.getId())).sum();
                carConsumption = calculateConsumptionFallback(
                        plausibleLogs, new BigDecimal(plausibleDistance));
                carDistance = plausibleDistance;
            }

            if (carConsumption != null && carDistance > 0) {
                totalWeightedConsumption = totalWeightedConsumption
                        .add(carConsumption.multiply(new BigDecimal(carDistance)));
                totalDistance += carDistance;
            }
        }

        if (totalDistance == 0) return null;

        return totalWeightedConsumption
                .divide(new BigDecimal(totalDistance), 2, RoundingMode.HALF_UP);
    }

    /**
     * Group charges by time period (DAY, WEEK, MONTH) and aggregate metrics.
     */
    private List<EvLogStatisticsResponse.ChargeDataPoint> groupChargesByPeriod(
            List<EvLog> logs, String groupBy, Map<UUID, Integer> distanceByLogId) {

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
                            .filter(c -> c != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Distance and consumption for this period
                    List<EvLog> logsWithDistance = periodLogs.stream()
                            .filter(l -> distanceByLogId.containsKey(l.getId()))
                            .collect(Collectors.toList());

                    BigDecimal periodDistance = null;
                    BigDecimal periodConsumption = null;
                    if (!logsWithDistance.isEmpty()) {
                        int distSum = logsWithDistance.stream()
                                .mapToInt(l -> distanceByLogId.get(l.getId())).sum();
                        periodDistance = new BigDecimal(distSum);
                        BigDecimal kwhForDistance = logsWithDistance.stream()
                                .map(EvLog::getKwhCharged).reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (periodDistance.compareTo(BigDecimal.ZERO) > 0) {
                            periodConsumption = kwhForDistance
                                    .multiply(new BigDecimal("100"))
                                    .divide(periodDistance, 2, RoundingMode.HALF_UP);
                        }
                    }

                    LocalDateTime periodTimestamp = periodLogs.get(0).getLoggedAt();
                    return new EvLogStatisticsResponse.ChargeDataPoint(
                            periodTimestamp, totalCost, totalKwh, periodDistance, periodConsumption
                    );
                })
                .collect(Collectors.toList());
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
}
