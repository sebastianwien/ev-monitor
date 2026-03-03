package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarCreateResponse;
import com.evmonitor.application.CarRequest;
import com.evmonitor.application.CarResponse;
import com.evmonitor.application.CarService;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cars")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @PostMapping
    public ResponseEntity<CarCreateResponse> createCar(@Valid @RequestBody CarRequest request, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        CarCreateResponse response = carService.createCar(principal.getUser().getId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CarResponse>> getAllCars(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        List<CarResponse> cars = carService.getCarsForUser(principal.getUser().getId());
        return ResponseEntity.ok(cars);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CarResponse> getCarById(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        CarResponse car = carService.getCarByIdForUser(id, principal.getUser().getId());
        return ResponseEntity.ok(car);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CarResponse> updateCar(@PathVariable UUID id, @Valid @RequestBody CarRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        CarResponse response = carService.updateCar(id, principal.getUser().getId(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCar(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        carService.deleteCar(id, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/brands")
    public ResponseEntity<List<BrandInfo>> getBrands() {
        List<BrandInfo> brands = Arrays.stream(CarBrand.values())
                .map(brand -> new BrandInfo(brand.name(), brand.getDisplayString()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/brands/{brand}/models")
    public ResponseEntity<List<ModelInfo>> getModelsForBrand(@PathVariable CarBrand brand) {
        List<ModelInfo> models = CarBrand.CarModel.byBrand(brand)
                .stream()
                .map(model -> new ModelInfo(
                        model.name(),
                        model.getDisplayName(),
                        model.getCapacities()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(models);
    }

    public record BrandInfo(String value, String label) {}

    public record ModelInfo(String value, String label, List<Double> capacities) {}
}
