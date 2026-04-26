package com.evmonitor.infrastructure.web;

import com.evmonitor.application.BatterySohService;
import com.evmonitor.domain.BatterySohRepository;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Internal API for service-to-service communication (connectors, wallbox).
 * Protected by X-Internal-Token header.
 */
@RestController
@RequestMapping("/api/internal/cars")
@RequiredArgsConstructor
public class InternalCarController {

    private final CarRepository carRepository;
    private final BatterySohRepository sohRepository;
    private final BatterySohService batterySohService;

    /**
     * Returns the SoH-adjusted effective battery capacity.
     * Falls back to nominal spec capacity when no SoH data is available.
     */
    @GetMapping("/{carId}/battery-capacity")
    public ResponseEntity<Map<String, BigDecimal>> getBatteryCapacity(@PathVariable UUID carId) {
        Car car = carRepository.findById(carId).orElse(null);
        if (car == null || car.getBatteryCapacityKwh() == null) {
            return ResponseEntity.notFound().build();
        }
        BigDecimal effective = car.getEffectiveBatteryCapacityKwhAt(
                LocalDate.now(), sohRepository.findByCarId(carId));
        return ResponseEntity.ok(Map.of("batteryCapacityKwh", effective));
    }

    @PostMapping("/{carId}/soh/bms-derived")
    public ResponseEntity<Void> persistBmsDerivedSoh(
            @PathVariable UUID carId,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal derivedCapacityKwh = body.get("derivedCapacityKwh");
        if (derivedCapacityKwh == null) return ResponseEntity.badRequest().build();
        batterySohService.persistBmsDerived(carId, derivedCapacityKwh);
        return ResponseEntity.ok().build();
    }
}
