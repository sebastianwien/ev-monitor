package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import org.springframework.stereotype.Service;

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

    public EvLogService(EvLogRepository evLogRepository, CarRepository carRepository) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
    }

    public EvLogResponse logCharging(UUID userId, EvLogRequest request) {
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
                request.loggedAt());

        EvLog savedLog = evLogRepository.save(newLog);
        return EvLogResponse.fromDomain(savedLog);
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

        // Get all logs for this car
        List<EvLog> logs = evLogRepository.findAllByCarId(carId);

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

        // Charge over time data (grouped and sorted by time)
        List<EvLogStatisticsResponse.ChargeDataPoint> chargesOverTime =
                groupChargesByPeriod(logs, groupBy != null ? groupBy : "MONTH");

        return new EvLogStatisticsResponse(
                totalKwhCharged,
                totalCostEur,
                avgCostPerKwh,
                cheapestCharge,
                mostExpensiveCharge,
                avgChargeDuration,
                logs.size(),
                chargesOverTime
        );
    }

    private EvLogStatisticsResponse createEmptyStatistics() {
        return new EvLogStatisticsResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                List.of()
        );
    }

    /**
     * Group charges by time period (DAY, WEEK, MONTH) and aggregate metrics.
     */
    private List<EvLogStatisticsResponse.ChargeDataPoint> groupChargesByPeriod(
            List<EvLog> logs, String groupBy) {

        // Group logs by period
        Map<String, List<EvLog>> groupedLogs = new java.util.LinkedHashMap<>();

        for (EvLog log : logs) {
            String periodKey = getPeriodKey(log.getLoggedAt(), groupBy);
            groupedLogs.computeIfAbsent(periodKey, k -> new ArrayList<>()).add(log);
        }

        // Aggregate each period
        return groupedLogs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<EvLog> periodLogs = entry.getValue();

                    // Sum up kWh and cost for this period
                    BigDecimal totalKwh = periodLogs.stream()
                            .map(EvLog::getKwhCharged)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalCost = periodLogs.stream()
                            .map(EvLog::getCostEur)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Use first log's timestamp as representative for this period
                    LocalDateTime periodTimestamp = periodLogs.get(0).getLoggedAt();

                    return new EvLogStatisticsResponse.ChargeDataPoint(
                            periodTimestamp,
                            totalCost,
                            totalKwh
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
