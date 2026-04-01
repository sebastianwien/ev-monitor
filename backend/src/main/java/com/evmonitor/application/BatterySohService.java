package com.evmonitor.application;

import com.evmonitor.domain.BatterySohEntry;
import com.evmonitor.domain.BatterySohRepository;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BatterySohService {

    private final BatterySohRepository sohRepository;
    private final CarRepository carRepository;

    public BatterySohService(BatterySohRepository sohRepository, CarRepository carRepository) {
        this.sohRepository = sohRepository;
        this.carRepository = carRepository;
    }

    public List<BatterySohResponse> getHistory(UUID carId, UUID userId) {
        verifyOwnership(carId, userId);
        return sohRepository.findByCarId(carId)
                .stream()
                .map(BatterySohResponse::fromDomain)
                .toList();
    }

    @Transactional
    public BatterySohResponse addMeasurement(UUID carId, UUID userId, BatterySohRequest request) {
        verifyOwnership(carId, userId);

        BatterySohEntry entry = new BatterySohEntry(
                UUID.randomUUID(),
                carId,
                request.sohPercent(),
                request.recordedAt(),
                LocalDateTime.now());

        BatterySohEntry saved = sohRepository.save(entry);
        syncDegradationToCarField(carId);
        return BatterySohResponse.fromDomain(saved);
    }

    /**
     * Korrigiert den aktuellsten Eintrag (Tippfehler-Korrektur, kein neuer historischer Eintrag).
     */
    @Transactional
    public BatterySohResponse updateLatest(UUID entryId, UUID carId, UUID userId, BatterySohRequest request) {
        verifyOwnership(carId, userId);

        BatterySohEntry existing = sohRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("SoH entry not found"));

        if (!existing.getCarId().equals(carId)) {
            throw new IllegalArgumentException("Entry does not belong to this car");
        }

        BatterySohEntry updated = new BatterySohEntry(
                existing.getId(),
                existing.getCarId(),
                request.sohPercent(),
                request.recordedAt(),
                existing.getCreatedAt());

        BatterySohEntry saved = sohRepository.save(updated);
        syncDegradationToCarField(carId);
        return BatterySohResponse.fromDomain(saved);
    }

    @Transactional
    public void deleteMeasurement(UUID entryId, UUID carId, UUID userId) {
        verifyOwnership(carId, userId);

        BatterySohEntry entry = sohRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("SoH entry not found"));

        if (!entry.getCarId().equals(carId)) {
            throw new IllegalArgumentException("Entry does not belong to this car");
        }

        sohRepository.deleteById(entryId);
        syncDegradationToCarField(carId);
    }

    /**
     * Hält battery_degradation_percent auf car als denormalisierten Cache-Wert aktuell
     * (zeigt immer den aktuellsten SoH-Eintrag als Degradation).
     * So funktionieren alle bestehenden Abfragen ohne Änderung weiter.
     */
    private void syncDegradationToCarField(UUID carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));

        List<BatterySohEntry> history = sohRepository.findByCarId(carId);
        BigDecimal degradation = history.isEmpty() ? null
                : BigDecimal.valueOf(100).subtract(history.get(0).getSohPercent());

        Car updated = new Car(
                car.getId(), car.getUserId(), car.getModel(), car.getYear(),
                car.getLicensePlate(), car.getTrim(), car.getBatteryCapacityKwh(), car.getPowerKw(),
                car.getRegistrationDate(), car.getDeregistrationDate(), car.getStatus(),
                car.getCreatedAt(), LocalDateTime.now(), car.getImagePath(), car.isImagePublic(),
                car.isPrimary(), degradation, car.isBusinessCar(), car.isHeatPump());

        carRepository.save(updated);
    }

    private void verifyOwnership(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }
    }
}
