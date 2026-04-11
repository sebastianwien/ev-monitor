package com.evmonitor.infrastructure.web;

import com.evmonitor.application.VehicleSpecificationCreateResponse;
import com.evmonitor.application.VehicleSpecificationRequest;
import com.evmonitor.application.VehicleSpecificationResponse;
import com.evmonitor.application.VehicleSpecificationService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/api/vehicle-specifications")
@Validated
@RequiredArgsConstructor
public class VehicleSpecificationController {

    private final VehicleSpecificationService vehicleSpecificationService;

    /**
     * Lookup WLTP data for a specific vehicle configuration.
     * Returns 404 if no data exists.
     */
    @GetMapping("/lookup")
    public ResponseEntity<VehicleSpecificationResponse> lookup(
            @RequestParam @NotBlank @Size(max = 100) String brand,
            @RequestParam @NotBlank @Size(max = 100) String model,
            @RequestParam @NotNull @Positive @DecimalMax("500.0") BigDecimal capacityKwh) {

        Optional<VehicleSpecificationResponse> result = vehicleSpecificationService.lookup(brand, model, capacityKwh);

        return result
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create new WLTP data entry.
     * Requires authentication and awards coins to the user.
     */
    @PostMapping
    public ResponseEntity<VehicleSpecificationCreateResponse> create(
            @Valid @RequestBody VehicleSpecificationRequest request,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        VehicleSpecificationCreateResponse response = vehicleSpecificationService.create(
                principal.getUser().getId(),
                request
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
