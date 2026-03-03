package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvLogService {

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final CoinLogService coinLogService;

    public EvLogService(EvLogRepository evLogRepository, CarRepository carRepository, UserRepository userRepository, CoinLogService coinLogService) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.userRepository = userRepository;
        this.coinLogService = coinLogService;
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
                request.loggedAt());

        EvLog savedLog = evLogRepository.save(newLog);

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
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        return evLogRepository.findAllByCarId(carId)
                .stream()
                .map(EvLogResponse::fromDomain)
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
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgCostPerKwh = totalKwhCharged.compareTo(BigDecimal.ZERO) > 0
                ? totalCostEur.divide(totalKwhCharged, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal cheapestCharge = logs.stream()
                .map(EvLog::getCostEur)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal mostExpensiveCharge = logs.stream()
                .map(EvLog::getCostEur)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        Integer avgChargeDuration = (int) logs.stream()
                .mapToInt(EvLog::getChargeDurationMinutes)
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

        // Total distance and avg consumption (only logs that have distance data)
        BigDecimal totalDistanceKm = null;
        BigDecimal avgConsumptionKwhPer100km = null;
        List<EvLog> filteredLogsWithDistance = logs.stream()
                .filter(l -> distanceByLogId.containsKey(l.getId()) && distanceByLogId.get(l.getId()) > 0)
                .collect(Collectors.toList());
        if (!filteredLogsWithDistance.isEmpty()) {
            int totalDistanceInt = filteredLogsWithDistance.stream()
                    .mapToInt(l -> distanceByLogId.get(l.getId())).sum();
            totalDistanceKm = new BigDecimal(totalDistanceInt);
            BigDecimal kwhForThoseTrips = filteredLogsWithDistance.stream()
                    .map(EvLog::getKwhCharged).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalDistanceKm.compareTo(BigDecimal.ZERO) > 0) {
                avgConsumptionKwhPer100km = kwhForThoseTrips
                        .multiply(new BigDecimal("100"))
                        .divide(totalDistanceKm, 2, RoundingMode.HALF_UP);
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
     * Compute distance driven before each charge from consecutive odometer readings.
     * Distance for log[i] = log[i].odometer - log[i-1].odometer (if both have odometer data).
     * Logs must be sorted by loggedAt ascending.
     */
    private Map<UUID, Integer> computeDistanceByLogId(List<EvLog> sortedLogs) {
        Map<UUID, Integer> result = new java.util.HashMap<>();
        for (int i = 1; i < sortedLogs.size(); i++) {
            EvLog current = sortedLogs.get(i);
            EvLog previous = sortedLogs.get(i - 1);
            if (current.getOdometerKm() != null && previous.getOdometerKm() != null) {
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
