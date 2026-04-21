package com.evmonitor.application;

import com.evmonitor.domain.CoinType;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.domain.VehicleSpecificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleSpecificationService {

    private static final Logger log = LoggerFactory.getLogger(VehicleSpecificationService.class);
    private static final int CONTRIBUTION_COINS = 50;

    private final VehicleSpecificationRepository vehicleSpecificationRepository;
    private final CoinLogService coinLogService;

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
            log.warn("Unknown ratingSource '{}' in lookup — falling back to WLTP", ratingSource);
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

        // Parse ratingSource — null or missing defaults to WLTP (backward compat)
        VehicleSpecification.RatingSource ratingSource;
        try {
            ratingSource = (request.ratingSource() == null)
                    ? VehicleSpecification.RatingSource.WLTP
                    : VehicleSpecification.RatingSource.valueOf(request.ratingSource());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ratingSource: " + request.ratingSource() + ". Allowed: WLTP, EPA");
        }

        // Duplicate check is per (brand, model, capacity, type, ratingSource)
        boolean exists = vehicleSpecificationRepository.existsByCarBrandAndModelAndCapacityAndTypeAndSource(
                sanitizedBrand,
                sanitizedModel,
                request.batteryCapacityKwh(),
                VehicleSpecification.WltpType.COMBINED,
                ratingSource
        );

        if (exists) {
            throw new IllegalArgumentException(ratingSource.name() + " data already exists for this vehicle configuration");
        }

        VehicleSpecification newSpec = VehicleSpecification.createNew(
                sanitizedBrand,
                sanitizedModel,
                request.batteryCapacityKwh(),
                request.officialRangeKm(),
                request.officialConsumptionKwhPer100km(),
                VehicleSpecification.WltpType.COMBINED,
                ratingSource
        );

        VehicleSpecification saved;
        try {
            saved = vehicleSpecificationRepository.save(newSpec);
        } catch (DataIntegrityViolationException e) {
            // Race condition: Another request created the same entry between check and save
            throw new IllegalArgumentException(ratingSource.name() + " data already exists for this vehicle configuration (concurrent insert detected)");
        }

        // Award coins for community contribution
        // SECURITY: Sanitized inputs are used in the description to prevent injection in logs
        coinLogService.awardCoins(
                userId,
                CoinType.SOCIAL_COIN,
                CONTRIBUTION_COINS,
                String.format("%s data contribution: %s %s (%.1f kWh)",
                        ratingSource.name(), sanitizedBrand, sanitizedModel, request.batteryCapacityKwh())
        );

        return new VehicleSpecificationCreateResponse(
                VehicleSpecificationResponse.fromDomain(saved),
                CONTRIBUTION_COINS
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
