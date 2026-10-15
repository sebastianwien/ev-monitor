package com.evmonitor.infrastructure.web;

import com.evmonitor.application.VehicleSpecificationResponse;
import com.evmonitor.application.VehicleSpecificationService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Lesezugriff auf Spec-Zeilen. Das Nutzer-Anlegen (POST) entfiel mit A2; Zeilen entstehen nur im Admin-Tab,
 * Lücken melden Nutzer über {@code /api/vehicle-requests}. {@code /lookup} bleibt bis Release B.
 */
@RestController
@RequestMapping("/api/vehicle-specifications")
@Validated
@RequiredArgsConstructor
public class VehicleSpecificationController {

    private final VehicleSpecificationService vehicleSpecificationService;

    @GetMapping("/{id}")
    public ResponseEntity<VehicleSpecificationResponse> getById(@PathVariable UUID id) {
        return vehicleSpecificationService.lookupById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lookup official rating data for a specific vehicle configuration.
     * ratingSource defaults to WLTP; pass "EPA" for US users.
     * Returns 404 if no data exists.
     */
    @GetMapping("/lookup")
    public ResponseEntity<VehicleSpecificationResponse> lookup(
            @RequestParam @NotBlank @Size(max = 100) String brand,
            @RequestParam @NotBlank @Size(max = 100) String model,
            @RequestParam @NotNull @Positive @DecimalMax("500.0") BigDecimal capacityKwh,
            @RequestParam(required = false, defaultValue = "WLTP") @Pattern(regexp = "WLTP|EPA") String ratingSource) {

        Optional<VehicleSpecificationResponse> result = vehicleSpecificationService.lookup(brand, model, capacityKwh, ratingSource);

        return result
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
