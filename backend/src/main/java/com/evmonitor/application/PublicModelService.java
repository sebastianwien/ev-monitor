package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PublicModelService {

    private final JpaEvLogRepository evLogRepository;
    private final JpaVehicleSpecificationRepository vehicleSpecificationRepository;
    private final JpaUserRepository userRepository;

    public PublicModelService(JpaEvLogRepository evLogRepository,
                              JpaVehicleSpecificationRepository vehicleSpecificationRepository,
                              JpaUserRepository userRepository) {
        this.evLogRepository = evLogRepository;
        this.vehicleSpecificationRepository = vehicleSpecificationRepository;
        this.userRepository = userRepository;
    }

    @Cacheable("platformStats")
    public PlatformStatsResponse getPlatformStats() {
        int modelCount = (int) java.util.Arrays.stream(CarBrand.CarModel.values())
                .filter(m -> m != CarBrand.CarModel.UNKNOWN)
                .count();
        long userCount = userRepository.countBySeedDataFalseAndEmailVerifiedTrue();
        return new PlatformStatsResponse(modelCount, userCount);
    }

    /**
     * Returns public, anonymized statistics for a car model.
     * Excludes all data from seed/test users.
     * Returns Optional.empty() if the model enum doesn't exist.
     */
    @Cacheable("modelStats")
    public Optional<PublicModelStatsResponse> getModelStats(String brandName, String modelName,
                                                             UUID currentUserId, boolean isSeedUser) {
        // Validate that the model actually exists in our enum
        // Replace spaces with underscores (e.g. "Polestar 2" -> "POLESTAR_2")
        CarBrand.CarModel carModel;
        try {
            String normalizedModelName = modelName.replace(" ", "_").toUpperCase();
            carModel = CarBrand.CarModel.valueOf(normalizedModelName);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String modelEnumName = carModel.name();
        String brandEnumName = carModel.getBrand().name();

        // Get proper display names from enum (preserves BMW, VW, MG etc.)
        String brandDisplay = carModel.getBrand().getDisplayString();
        String modelDisplay = carModel.getDisplayName();

        // Fetch basic aggregated stats (count, cost, kwh)
        // Demo Mode: If seed user, includes ALL seed data (not just current user)
        Object[] basicStats = evLogRepository.findPublicBasicStatsByModel(
                modelEnumName, isSeedUser);

        long logCount = 0;
        int uniqueContributors = 0;
        BigDecimal avgCostPerKwh = null;
        BigDecimal avgKwhPerSession = null;

        if (basicStats != null && basicStats.length > 0) {
            try {
                // Native queries can return nested arrays in some cases, unwrap if needed
                Object firstElement = basicStats[0];

                // If first element is itself an array, unwrap it
                if (firstElement instanceof Object[]) {
                    basicStats = (Object[]) firstElement;
                }

                if (basicStats[0] != null) {
                    logCount = ((Number) basicStats[0]).longValue();
                }
                if (basicStats.length > 1 && basicStats[1] != null) {
                    uniqueContributors = ((Number) basicStats[1]).intValue();
                }
                if (basicStats.length > 2 && basicStats[2] != null) {
                    avgCostPerKwh = toBigDecimal(basicStats[2]).setScale(4, RoundingMode.HALF_UP);
                }
                if (basicStats.length > 3 && basicStats[3] != null) {
                    avgKwhPerSession = toBigDecimal(basicStats[3]).setScale(2, RoundingMode.HALF_UP);
                }
            } catch (Exception e) {
                log.error("Failed to parse basic stats for model {}: {}", modelEnumName, e.getMessage());
            }
        }

        // Fetch consumption (may be null if no odometer data)
        // Demo Mode: If seed user, includes ALL seed data
        BigDecimal avgConsumption = evLogRepository.findAvgConsumptionByModel(
                modelEnumName, isSeedUser);
        if (avgConsumption != null) {
            avgConsumption = avgConsumption.setScale(2, RoundingMode.HALF_UP);
        }

        // Fetch WLTP variants for this model
        List<VehicleSpecificationEntity> wltpEntities =
                vehicleSpecificationRepository.findByCarModelOrderByBatteryCapacityKwhAsc(modelEnumName);

        List<PublicModelStatsResponse.WltpVariant> wltpVariants = wltpEntities.stream()
                .map(e -> new PublicModelStatsResponse.WltpVariant(
                        e.getBatteryCapacityKwh(),
                        e.getWltpRangeKm(),
                        e.getWltpConsumptionKwhPer100km()))
                .toList();

        String displayName = brandDisplay + " " + modelDisplay;

        return Optional.of(new PublicModelStatsResponse(
                brandEnumName,
                modelEnumName,
                displayName,
                (int) logCount,
                uniqueContributors,
                avgCostPerKwh,
                avgKwhPerSession,
                avgConsumption,
                wltpVariants
        ));
    }

    /**
     * Returns a list of all models that have at least one WLTP entry
     * AND real community data (logs with include_in_statistics = true).
     * If authenticated as seed user, includes models with their own seed data.
     * Returns format: "BRAND/MODEL" (e.g., "TESLA/MODEL_3")
     */
    @Cacheable("modelsWithData")
    public List<String> getModelsWithWltpData(UUID currentUserId, boolean isSeedUser) {
        // Get all models with WLTP data
        List<String> modelsWithWltp = vehicleSpecificationRepository.findAll().stream()
                .map(VehicleSpecificationEntity::getCarModel)
                .distinct()
                .toList();

        // Filter to only include models that have real community logs
        // Demo Mode: If seed user, includes ALL seed data
        return modelsWithWltp.stream()
                .filter(model -> {
                    Object[] stats = evLogRepository.findPublicBasicStatsByModel(
                            model, isSeedUser);
                    if (stats == null || stats.length == 0) {
                        return false;
                    }

                    // Unwrap nested array if needed
                    Object firstElement = stats[0];
                    if (firstElement instanceof Object[]) {
                        stats = (Object[]) firstElement;
                    }

                    // Check if logCount > 0
                    if (stats[0] != null) {
                        long logCount = ((Number) stats[0]).longValue();
                        return logCount > 0;
                    }
                    return false;
                })
                .map(modelName -> {
                    // Get brand displayString and model displayName from CarModel enum
                    try {
                        CarBrand.CarModel carModel = CarBrand.CarModel.valueOf(modelName);
                        String brandDisplay = carModel.getBrand().getDisplayString();
                        String modelDisplay = carModel.getDisplayName();
                        return brandDisplay + "/" + modelDisplay;
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown CarModel enum: {}", modelName);
                        return "Unknown/" + modelName;
                    }
                })
                .sorted()
                .toList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        if (value instanceof Float f) return BigDecimal.valueOf(f);
        return new BigDecimal(value.toString());
    }
}
