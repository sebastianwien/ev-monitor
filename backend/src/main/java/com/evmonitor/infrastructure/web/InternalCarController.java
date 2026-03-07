package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Internal API for service-to-service communication (connectors, wallbox).
 * Protected by X-Internal-Token header.
 */
@RestController
@RequestMapping("/api/internal/cars")
public class InternalCarController {

    private final CarRepository carRepository;

    public InternalCarController(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    @GetMapping("/{carId}/battery-capacity")
    public ResponseEntity<Map<String, BigDecimal>> getBatteryCapacity(@PathVariable UUID carId) {
        Car car = carRepository.findById(carId).orElse(null);
        if (car == null || car.getBatteryCapacityKwh() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("batteryCapacityKwh", car.getBatteryCapacityKwh()));
    }
}
