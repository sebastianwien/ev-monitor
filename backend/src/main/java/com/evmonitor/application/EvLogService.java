package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.weather.TemperatureEnrichmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    public EvLogService(EvLogRepository evLogRepository, CarRepository carRepository, UserRepository userRepository,
            CoinLogService coinLogService, TemperatureEnrichmentService temperatureEnrichmentService) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.coinLogService = coinLogService;
        this.temperatureEnrichmentService = temperatureEnrichmentService;
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

        List<EvLog> logs = (limit != null && limit > 0)
                ? evLogRepository.findLatestByCarId(carId, limit, page)
                : evLogRepository.findAllByCarId(carId);

        return logs.stream().map(EvLogResponse::fromDomain).toList();
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
            avgConsumptionKwhPer100km = calculateConsumptionWithSoc(allLogsForCar, filteredLogsWithDistance, car.getBatteryCapacityKwh());

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
     * Calculate avg consumption using SoC data if available (more accurate for partial charging).
     *
     * General formula for consumption between two anchor points (logs with odometer):
     * 1. Find anchor Y (previous log with odometer) and anchor X (current log with odometer)
     * 2. Distance = odometerX - odometerY
     * 3. Energy consumed = socY_after + Σ(kWh charged between Y and X) - socX_before
     * 4. Consumption = (Energy consumed / Distance) × 100
     *
     * This handles logs without odometer by including their charged energy in the calculation.
     *
     * @param allLogs All logs sorted by time
     * @param logsWithDistance Filtered logs that have distance data (anchor points)
     * @param batteryCapacityKwh Battery capacity of the car
     * @return Avg consumption in kWh/100km, or null if SoC data missing
     */
    private BigDecimal calculateConsumptionWithSoc(List<EvLog> allLogs, List<EvLog> logsWithDistance, BigDecimal batteryCapacityKwh) {
        if (batteryCapacityKwh == null || batteryCapacityKwh.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // Can't calculate without battery capacity
        }

        BigDecimal totalEnergyConsumed = BigDecimal.ZERO;
        int totalDistance = 0;
        int validTrips = 0;

        for (EvLog anchorX : logsWithDistance) {
            // Find previous anchor point (log with odometer)
            EvLog anchorY = findPreviousLog(allLogs, anchorX);
            if (anchorY == null) {
                continue; // First log, no previous anchor
            }

            // Check if both anchors have required SoC data
            if (anchorX.getSocAfterChargePercent() == null || anchorY.getSocAfterChargePercent() == null) {
                continue; // Missing SoC data, skip this trip
            }
            if (anchorX.getKwhCharged() == null || anchorY.getKwhCharged() == null) {
                continue; // Missing kWh data, skip this trip
            }

            // Calculate distance between anchors
            if (anchorX.getOdometerKm() == null || anchorY.getOdometerKm() == null) {
                continue;
            }
            int distance = anchorX.getOdometerKm() - anchorY.getOdometerKm();
            if (distance <= 0) {
                continue; // Invalid distance
            }

            // Find all logs between the two anchors (excl. both ends)
            List<EvLog> logsBetween = findLogsBetween(allLogs, anchorY, anchorX);

            // Check if all logs between have required data
            boolean allHaveData = logsBetween.stream()
                .allMatch(log -> log.getKwhCharged() != null && log.getSocAfterChargePercent() != null);
            if (!allHaveData) {
                continue; // Missing data in logs between, skip this trip
            }

            // Sum all charged energy between anchors (excl. both ends)
            BigDecimal kwhChargedBetween = logsBetween.stream()
                .map(EvLog::getKwhCharged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calculate energy consumed:
            // Start (anchor Y after charge) + Loaded between - End (anchor X before charge)
            BigDecimal socYAfterKwh = new BigDecimal(anchorY.getSocAfterChargePercent())
                .multiply(batteryCapacityKwh)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            BigDecimal socXBeforePercent = calculateSocBeforeCharge(
                anchorX.getSocAfterChargePercent(),
                anchorX.getKwhCharged(),
                batteryCapacityKwh
            );
            BigDecimal socXBeforeKwh = socXBeforePercent
                .multiply(batteryCapacityKwh)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            BigDecimal energyConsumedKwh = socYAfterKwh
                .add(kwhChargedBetween)
                .subtract(socXBeforeKwh);

            // Skip if energy consumed is negative (invalid data)
            if (energyConsumedKwh.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            totalEnergyConsumed = totalEnergyConsumed.add(energyConsumedKwh);
            totalDistance += distance;
            validTrips++;
        }

        // Calculate average consumption if we have valid data
        if (validTrips == 0 || totalDistance == 0) {
            return null; // Not enough SoC data
        }

        return totalEnergyConsumed
            .multiply(new BigDecimal("100"))
            .divide(new BigDecimal(totalDistance), 2, RoundingMode.HALF_UP);
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
     * Calculate SoC before charging based on kWh charged and battery capacity.
     */
    private BigDecimal calculateSocBeforeCharge(int socAfterCharge, BigDecimal kwhCharged, BigDecimal batteryCapacityKwh) {
        BigDecimal socDelta = kwhCharged
            .divide(batteryCapacityKwh, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        return new BigDecimal(socAfterCharge).subtract(socDelta);
    }

    /**
     * Find the previous log before the given log that has odometer data.
     * Skips logs without odometer to find the actual previous trip.
     */
    private EvLog findPreviousLog(List<EvLog> sortedLogs, EvLog currentLog) {
        boolean foundCurrent = false;
        for (int i = 0; i < sortedLogs.size(); i++) {
            if (sortedLogs.get(i).getId().equals(currentLog.getId())) {
                foundCurrent = true;
                // Search backwards for log with odometer data
                for (int j = i - 1; j >= 0; j--) {
                    if (sortedLogs.get(j).getOdometerKm() != null) {
                        return sortedLogs.get(j);
                    }
                }
                return null; // No previous log with odometer found
            }
        }
        return null; // Current log not found in list
    }

    /**
     * Find all logs between two anchor logs (exclusive of both ends).
     * Used to include charged energy from logs without odometer data.
     *
     * @param sortedLogs All logs sorted by time
     * @param anchorY Earlier anchor (with odometer)
     * @param anchorX Later anchor (with odometer)
     * @return List of logs between the two anchors (empty if none)
     */
    private List<EvLog> findLogsBetween(List<EvLog> sortedLogs, EvLog anchorY, EvLog anchorX) {
        List<EvLog> result = new java.util.ArrayList<>();
        boolean foundY = false;

        for (EvLog log : sortedLogs) {
            if (log.getId().equals(anchorY.getId())) {
                foundY = true;
                continue; // Skip anchor Y itself
            }

            if (log.getId().equals(anchorX.getId())) {
                break; // Stop at anchor X (don't include it)
            }

            if (foundY) {
                result.add(log); // Add all logs between Y and X
            }
        }

        return result;
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
