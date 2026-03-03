package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarCreateResponse;
import com.evmonitor.application.CarImageService;
import com.evmonitor.application.CarRequest;
import com.evmonitor.application.CarResponse;
import com.evmonitor.application.CarService;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cars")
public class CarController {

    private final CarService carService;
    private final CarImageService carImageService;

    public CarController(CarService carService, CarImageService carImageService) {
        this.carService = carService;
        this.carImageService = carImageService;
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

    @PostMapping("/{id}/image")
    public ResponseEntity<CarResponse> uploadCarImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic,
            Authentication authentication) throws IOException {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        CarResponse response = carService.uploadCarImage(principal.getUser().getId(), id, file, isPublic);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getCarImage(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID requestingUserId = principal.getUser().getId();

        com.evmonitor.domain.Car car = carService.getCarById(id);

        // Private images are only visible to the owner
        if (!car.isImagePublic() && !car.getUserId().equals(requestingUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Resource> resource = carImageService.getImageResource(id);
        if (resource.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(resource.get());
    }

    @PatchMapping("/{id}/image")
    public ResponseEntity<CarResponse> updateCarImageVisibility(
            @PathVariable UUID id,
            @RequestParam boolean isPublic,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        CarResponse response = carService.updateCarImageVisibility(principal.getUser().getId(), id, isPublic);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/image")
    public ResponseEntity<Void> deleteCarImage(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        carService.deleteCarImage(principal.getUser().getId(), id);
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
