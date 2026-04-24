package com.evmonitor.application;

import com.evmonitor.domain.BatterySohEntry;
import com.evmonitor.domain.BatterySohRepository;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatterySohService {

    private static final BigDecimal SOH_CHANGE_THRESHOLD = new BigDecimal("2.0");

    private final BatterySohRepository sohRepository;
    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;

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

        Car updated = car.toBuilder()
                .batteryDegradationPercent(degradation)
                .updatedAt(LocalDateTime.now())
                .build();

        carRepository.save(updated);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSohAutoDetectEvent(SohAutoDetectEvent event) {
        autoDetectAndPersist(event.car());
    }

    /**
     * Derives SoH from AT_VEHICLE logs via median capacity estimation and persists the result.
     * Skips if: no qualifying logs, entry already exists for today, or change is <= 2% vs. last entry.
     * Called directly in tests; triggered via SohAutoDetectEvent in production.
     */
    @Transactional
    public void autoDetectAndPersist(Car car) {
        if (car.getBatteryCapacityKwh() == null) return;

        List<BatterySohEntry> history = sohRepository.findByCarId(car.getId());
        LocalDate today = LocalDate.now();
        if (history.stream().anyMatch(e -> e.getRecordedAt().equals(today))) return;

        Optional<BigDecimal> detected = BatterySohAutoDetector.detectSohPercent(
                evLogRepository.findRecentAtVehicleLogsWithSoc(car.getId(), BatterySohAutoDetector.ROLLING_WINDOW_SIZE * 3),
                car.getBatteryCapacityKwh());

        if (detected.isEmpty()) return;

        BigDecimal newSoh = detected.get();

        if (!history.isEmpty()) {
            BigDecimal lastSoh = history.get(0).getSohPercent();
            if (newSoh.subtract(lastSoh).abs().compareTo(SOH_CHANGE_THRESHOLD) <= 0) return;
        }

        sohRepository.save(new BatterySohEntry(UUID.randomUUID(), car.getId(), newSoh, today, LocalDateTime.now()));
        syncDegradationToCarField(car.getId());
    }

    /**
     * Runs SoH auto-detection for all cars that have AT_VEHICLE logs but no SoH entry
     * in the current calendar year. Skips cars where detection yields no result.
     *
     * @return number of cars for which a new SoH entry was persisted
     */
    @Transactional
    public int redetectForCarsWithoutSohThisYear() {
        List<Car> cars = carRepository.findCarsNeedingSohDetection();
        int detected = 0;
        for (Car car : cars) {
            long before = sohRepository.findByCarId(car.getId()).size();
            autoDetectAndPersist(car);
            long after = sohRepository.findByCarId(car.getId()).size();
            if (after > before) detected++;
        }
        return detected;
    }

    private void verifyOwnership(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }
    }
}
