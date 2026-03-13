package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
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
    private final CarRepository carRepository;
    private final EvLogService evLogService;

    public PublicModelService(JpaEvLogRepository evLogRepository,
                              JpaVehicleSpecificationRepository vehicleSpecificationRepository,
                              JpaUserRepository userRepository,
                              CarRepository carRepository,
                              EvLogService evLogService) {
        this.evLogRepository = evLogRepository;
        this.vehicleSpecificationRepository = vehicleSpecificationRepository;
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.evLogService = evLogService;
    }

    @Cacheable("platformStats")
    public PlatformStatsResponse getPlatformStats() {
        int modelCount = CarBrand.CarModel.values().length;
        long userCount = userRepository.countBySeedDataFalseAndEmailVerifiedTrue();
        long validTripCount = evLogRepository.countValidTrips();
        return new PlatformStatsResponse(modelCount, userCount, validTripCount);
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
        // Match by brand displayString + model displayName (case-insensitive)
        CarBrand.CarModel carModel = null;
        for (CarBrand.CarModel model : CarBrand.CarModel.values()) {
            if (model.getBrand().getDisplayString().equalsIgnoreCase(brandName) &&
                model.getDisplayName().replace(" ", "_").equalsIgnoreCase(modelName.replace(" ", "_"))) {
                carModel = model;
                break;
            }
        }

        if (carModel == null) {
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

        // Fetch consumption via EvLogService (authoritative calculation, same logic as user stats)
        // Demo Mode: If seed user, includes ALL seed data
        List<Car> carsForModel = carRepository.findAllByModel(carModel);
        CommunityConsumptionResult communityResult = evLogService.calculateCommunityAvgConsumption(carsForModel, isSeedUser);
        BigDecimal avgConsumption = communityResult.value() != null
                ? communityResult.value().setScale(2, RoundingMode.HALF_UP) : null;

        // Fetch seasonal distribution via EvLogService (same SoC-based logic as overall consumption)
        // Demo Mode: If seed user, includes ALL seed data
        PublicModelStatsResponse.SeasonalDistribution seasonalDistribution = null;
        EvLogService.SeasonalConsumptionResult seasonal = evLogService.calculateSeasonalConsumption(carsForModel, isSeedUser);
        long totalKm = (long) seasonal.summerKm() + seasonal.winterKm();
        if (totalKm > 0) {
            int summerPct = (int) Math.round((seasonal.summerKm() * 100.0) / totalKm);
            int winterPct = (int) Math.round((seasonal.winterKm() * 100.0) / totalKm);
            seasonalDistribution = new PublicModelStatsResponse.SeasonalDistribution(
                    summerPct, winterPct,
                    seasonal.summerConsumptionKwhPer100km() != null
                            ? seasonal.summerConsumptionKwhPer100km().setScale(1, RoundingMode.HALF_UP) : null,
                    seasonal.winterConsumptionKwhPer100km() != null
                            ? seasonal.winterConsumptionKwhPer100km().setScale(1, RoundingMode.HALF_UP) : null,
                    seasonal.totalConsumptionKwhPer100km() != null
                            ? seasonal.totalConsumptionKwhPer100km().setScale(1, RoundingMode.HALF_UP) : null,
                    seasonal.summerLogCount(), seasonal.winterLogCount());
        }

        // Fetch WLTP variants for this model
        List<VehicleSpecificationEntity> wltpEntities =
                vehicleSpecificationRepository.findByCarModelOrderByBatteryCapacityKwhAsc(modelEnumName);

        List<PublicModelStatsResponse.WltpVariant> wltpVariants = wltpEntities.stream()
                .map(e -> {
                    List<Car> carsForVariant = carsForModel.stream()
                            .filter(c -> c.getBatteryCapacityKwh() != null
                                    && c.getBatteryCapacityKwh().compareTo(e.getBatteryCapacityKwh()) == 0)
                            .toList();
                    CommunityConsumptionResult variantResult = carsForVariant.isEmpty()
                            ? CommunityConsumptionResult.EMPTY
                            : evLogService.calculateCommunityAvgConsumption(carsForVariant, isSeedUser);
                    BigDecimal variantConsumption = variantResult.value() != null
                            ? variantResult.value().setScale(1, RoundingMode.HALF_UP) : null;
                    return new PublicModelStatsResponse.WltpVariant(
                            e.getBatteryCapacityKwh(),
                            e.getWltpRangeKm(),
                            e.getWltpConsumptionKwhPer100km(),
                            variantConsumption,
                            variantResult.tripCount() > 0 ? variantResult.tripCount() : null);
                })
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
                communityResult.estimatedTripCount(),
                wltpVariants,
                seasonalDistribution
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

        // Filter to only include models with real community logs, keep count for sorting
        // Demo Mode: If seed user, includes ALL seed data
        record ModelWithCount(String modelName, long logCount) {}

        return modelsWithWltp.stream()
                .map(model -> {
                    Object[] stats = evLogRepository.findPublicBasicStatsByModel(model, isSeedUser);
                    if (stats == null || stats.length == 0) return new ModelWithCount(model, 0);
                    Object firstElement = stats[0];
                    if (firstElement instanceof Object[]) stats = (Object[]) firstElement;
                    long count = stats[0] != null ? ((Number) stats[0]).longValue() : 0;
                    return new ModelWithCount(model, count);
                })
                .filter(m -> m.logCount() > 0)
                .sorted((a, b) -> Long.compare(b.logCount(), a.logCount()))
                .map(m -> {
                    try {
                        CarBrand.CarModel carModel = CarBrand.CarModel.valueOf(m.modelName());
                        return carModel.getBrand().getDisplayString() + "/" + carModel.getDisplayName();
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown CarModel enum: {}", m.modelName());
                        return "Unknown/" + m.modelName();
                    }
                })
                .toList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        if (value instanceof Float f) return BigDecimal.valueOf(f);
        return new BigDecimal(value.toString());
    }
}
