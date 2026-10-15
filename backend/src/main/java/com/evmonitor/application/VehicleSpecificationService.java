package com.evmonitor.application;

import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Lesezugriff auf Spec-Zeilen für das Auto-Formular. Anlegen und Pflegen laufen seit A1/A2
 * ausschließlich über den Admin-Tab ({@code AdminVehicleSpecificationService}); fehlende Zeilen
 * melden Nutzer über {@code MissingVehicleRequestService}.
 */
@Service
@RequiredArgsConstructor
public class VehicleSpecificationService {

    private static final Logger log = LoggerFactory.getLogger(VehicleSpecificationService.class);

    private final VehicleSpecificationRepository vehicleSpecificationRepository;

    /**
     * Lookup official rating data for a specific car configuration.
     * ratingSource defaults to WLTP; pass "EPA" for US users.
     * Returns Optional.empty() if no data exists.
     */
    public Optional<VehicleSpecificationResponse> lookup(String carBrand, String carModel,
                                                          BigDecimal batteryCapacityKwh, String ratingSource) {
        String sanitizedBrand = sanitizeInput(carBrand);
        String sanitizedModel = sanitizeInput(carModel);
        VehicleSpecification.RatingSource source;
        try {
            source = (ratingSource == null) ? VehicleSpecification.RatingSource.WLTP
                    : VehicleSpecification.RatingSource.valueOf(ratingSource.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown ratingSource '{}' in lookup - falling back to WLTP", ratingSource);
            source = VehicleSpecification.RatingSource.WLTP;
        }

        if (source == VehicleSpecification.RatingSource.EPA) {
            return vehicleSpecificationRepository.findByCarBrandAndModelAndCapacityAndTypeAndSource(
                    sanitizedBrand, sanitizedModel, batteryCapacityKwh,
                    VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.EPA
            ).map(VehicleSpecificationResponse::fromDomain);
        }
        return vehicleSpecificationRepository.findByCarBrandAndModelAndCapacityAndType(
                sanitizedBrand, sanitizedModel, batteryCapacityKwh,
                VehicleSpecification.WltpType.COMBINED
        ).map(VehicleSpecificationResponse::fromDomain);
    }

    /** Backward-compat overload - defaults to WLTP. */
    public Optional<VehicleSpecificationResponse> lookup(String carBrand, String carModel, BigDecimal batteryCapacityKwh) {
        return lookup(carBrand, carModel, batteryCapacityKwh, "WLTP");
    }

    public Optional<VehicleSpecificationResponse> lookupById(UUID id) {
        return vehicleSpecificationRepository.findById(id).map(VehicleSpecificationResponse::fromDomain);
    }

    /** Removes HTML tags and trims whitespace before the value reaches a query. */
    private String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("<[^>]*>", "").trim();
    }
}
