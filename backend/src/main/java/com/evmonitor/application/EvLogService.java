package com.evmonitor.application;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.domain.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvLogService {

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final VehicleSpecificationRepository vehicleSpecificationRepository;

    public EvLogService(EvLogRepository evLogRepository, CarRepository carRepository,
                        VehicleSpecificationRepository vehicleSpecificationRepository) {
        this.evLogRepository = evLogRepository;
        this.carRepository = carRepository;
        this.vehicleSpecificationRepository = vehicleSpecificationRepository;
    }

    public EvLogResponse logDrive(UUID userId, EvLogRequest request) {
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
                request.distanceKm(),
                request.consumptionKwhPer100km(),
                request.outsideTempC(),
                request.drivingStyle(),
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
     * Includes key metrics, WLTP comparison, and consumption over time data.
     */
    public EvLogStatisticsResponse getStatistics(UUID carId, UUID userId) {
        // Verify ownership
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Get all logs for this car
        List<EvLog> logs = evLogRepository.findAllByCarId(carId);

        if (logs.isEmpty()) {
            return createEmptyStatistics(car);
        }

        // Calculate key metrics
        BigDecimal totalDistance = logs.stream()
                .map(EvLog::getDistanceKm)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgConsumption = logs.stream()
                .map(EvLog::getConsumptionKwhPer100km)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logs.size()), 2, RoundingMode.HALF_UP);

        BigDecimal bestConsumption = logs.stream()
                .map(EvLog::getConsumptionKwhPer100km)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal worstConsumption = logs.stream()
                .map(EvLog::getConsumptionKwhPer100km)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // Get WLTP data for comparison
        String carBrand = car.getModel().name().split("_")[0]; // Extract brand from model enum
        VehicleSpecification wltpSpec = vehicleSpecificationRepository
                .findByCarBrandAndModelAndCapacityAndType(
                        carBrand,
                        car.getModel().name(),
                        car.getBatteryCapacityKwh(),
                        VehicleSpecification.WltpType.COMBINED
                ).orElse(null);

        BigDecimal wltpRange = wltpSpec != null ? wltpSpec.getWltpRangeKm() : null;
        BigDecimal wltpConsumption = wltpSpec != null ? wltpSpec.getWltpConsumptionKwhPer100km() : null;
        BigDecimal wltpDifference = null;

        if (wltpConsumption != null && wltpConsumption.compareTo(BigDecimal.ZERO) > 0) {
            // Calculate percentage difference: (actual - wltp) / wltp * 100
            wltpDifference = avgConsumption.subtract(wltpConsumption)
                    .divide(wltpConsumption, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        // Consumption over time data (sorted by time)
        List<EvLogStatisticsResponse.ConsumptionDataPoint> consumptionOverTime = logs.stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt))
                .map(log -> new EvLogStatisticsResponse.ConsumptionDataPoint(
                        log.getLoggedAt(),
                        log.getConsumptionKwhPer100km(),
                        log.getOutsideTempC()
                ))
                .collect(Collectors.toList());

        return new EvLogStatisticsResponse(
                totalDistance,
                avgConsumption,
                bestConsumption,
                worstConsumption,
                logs.size(),
                wltpRange,
                wltpConsumption,
                wltpDifference,
                consumptionOverTime
        );
    }

    private EvLogStatisticsResponse createEmptyStatistics(Car car) {
        // Try to get WLTP data even if no logs exist
        String carBrand = car.getModel().name().split("_")[0];
        VehicleSpecification wltpSpec = vehicleSpecificationRepository
                .findByCarBrandAndModelAndCapacityAndType(
                        carBrand,
                        car.getModel().name(),
                        car.getBatteryCapacityKwh(),
                        VehicleSpecification.WltpType.COMBINED
                ).orElse(null);

        BigDecimal wltpRange = wltpSpec != null ? wltpSpec.getWltpRangeKm() : null;
        BigDecimal wltpConsumption = wltpSpec != null ? wltpSpec.getWltpConsumptionKwhPer100km() : null;

        return new EvLogStatisticsResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                wltpRange,
                wltpConsumption,
                null,
                List.of()
        );
    }
}
