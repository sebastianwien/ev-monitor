package com.evmonitor.application;

import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class VehicleSpecificationService {

    private static final int WLTP_CONTRIBUTION_COINS = 50;

    private final VehicleSpecificationRepository vehicleSpecificationRepository;
    private final CoinLogService coinLogService;

    public VehicleSpecificationService(VehicleSpecificationRepository vehicleSpecificationRepository,
                                       CoinLogService coinLogService) {
        this.vehicleSpecificationRepository = vehicleSpecificationRepository;
        this.coinLogService = coinLogService;
    }

    /**
     * Lookup WLTP data for a specific car configuration.
     * Returns Optional.empty() if no data exists.
     */
    public Optional<VehicleSpecificationResponse> lookup(String carBrand, String carModel, BigDecimal batteryCapacityKwh) {
        // Sanitize inputs (trim whitespace, prevent XSS)
        String sanitizedBrand = sanitizeInput(carBrand);
        String sanitizedModel = sanitizeInput(carModel);

        // Always look for COMBINED first (most common)
        return vehicleSpecificationRepository.findByCarBrandAndModelAndCapacityAndType(
                sanitizedBrand,
                sanitizedModel,
                batteryCapacityKwh,
                VehicleSpecification.WltpType.COMBINED
        ).map(VehicleSpecificationResponse::fromDomain);
    }

    /**
     * Create new WLTP data entry and award coins to the user.
     * Returns the created specification and the number of coins awarded.
     *
     * @Transactional ensures atomicity (all-or-nothing) and prevents race conditions
     */
    @Transactional
    public VehicleSpecificationCreateResponse create(UUID userId, VehicleSpecificationRequest request) {
        // Sanitize inputs to prevent XSS (trim whitespace, remove HTML tags)
        String sanitizedBrand = sanitizeInput(request.carBrand());
        String sanitizedModel = sanitizeInput(request.carModel());

        // Check if data already exists (always use COMBINED as per requirements)
        boolean exists = vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndType(
                sanitizedBrand,
                sanitizedModel,
                request.batteryCapacityKwh(),
                VehicleSpecification.WltpType.COMBINED
        );

        if (exists) {
            throw new IllegalArgumentException("WLTP data already exists for this vehicle configuration");
        }

        // Create new specification (implicitly COMBINED) with sanitized inputs
        VehicleSpecification newSpec = VehicleSpecification.createNew(
                sanitizedBrand,
                sanitizedModel,
                request.batteryCapacityKwh(),
                request.wltpRangeKm(),
                request.wltpConsumptionKwhPer100km(),
                VehicleSpecification.WltpType.COMBINED
        );

        VehicleSpecification saved;
        try {
            saved = vehicleSpecificationRepository.save(newSpec);
        } catch (DataIntegrityViolationException e) {
            // Race condition: Another request created the same entry between check and save
            throw new IllegalArgumentException("WLTP data already exists for this vehicle configuration (concurrent insert detected)");
        }

        // Award coins for community contribution
        // SECURITY: Sanitized inputs are used in the description to prevent injection in logs
        coinLogService.awardCoins(
                userId,
                CoinType.SOCIAL_COIN,
                WLTP_CONTRIBUTION_COINS,
                String.format("WLTP data contribution: %s %s (%.1f kWh)",
                        sanitizedBrand, sanitizedModel, request.batteryCapacityKwh())
        );

        return new VehicleSpecificationCreateResponse(
                VehicleSpecificationResponse.fromDomain(saved),
                WLTP_CONTRIBUTION_COINS
        );
    }

    /**
     * Sanitize user input to prevent XSS and injection attacks.
     * Removes HTML tags and trims whitespace.
     */
    private String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        // Remove HTML tags and trim whitespace
        return input.replaceAll("<[^>]*>", "").trim();
    }
}
