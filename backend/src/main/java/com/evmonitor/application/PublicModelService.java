package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.VehicleCategory;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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

        // Fetch AC/DC cost split (min 5 sessions per type, else null)
        BigDecimal acAvgCostPerKwh = null;
        BigDecimal dcAvgCostPerKwh = null;
        Object[] acDcStats = evLogRepository.findAcDcCostStatsByModel(modelEnumName, isSeedUser);
        if (acDcStats != null && acDcStats.length > 0) {
            if (acDcStats[0] instanceof Object[]) acDcStats = (Object[]) acDcStats[0];
            if (acDcStats.length >= 4) {
                if (acDcStats[0] != null) acAvgCostPerKwh = toBigDecimal(acDcStats[0]).setScale(4, RoundingMode.HALF_UP);
                if (acDcStats[2] != null) dcAvgCostPerKwh = toBigDecimal(acDcStats[2]).setScale(4, RoundingMode.HALF_UP);
            }
        }

        // Fetch average DC charging power from real fast-charging sessions (DC only, min 5 sessions)
        BigDecimal avgChargingPowerKw = evLogRepository.findAvgDcChargingPowerKwByModel(modelEnumName, isSeedUser);
        if (avgChargingPowerKw != null) {
            avgChargingPowerKw = avgChargingPowerKw.setScale(1, RoundingMode.HALF_UP);
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
                    PublicModelStatsResponse.SeasonalDistribution variantSeasonal = null;
                    if (!carsForVariant.isEmpty()) {
                        EvLogService.SeasonalConsumptionResult vs = evLogService.calculateSeasonalConsumption(carsForVariant, isSeedUser);
                        long vsKm = (long) vs.summerKm() + vs.winterKm();
                        if (vsKm > 0) {
                            int sPct = (int) Math.round((vs.summerKm() * 100.0) / vsKm);
                            int wPct = (int) Math.round((vs.winterKm() * 100.0) / vsKm);
                            variantSeasonal = new PublicModelStatsResponse.SeasonalDistribution(
                                    sPct, wPct,
                                    vs.summerConsumptionKwhPer100km() != null ? vs.summerConsumptionKwhPer100km().setScale(1, RoundingMode.HALF_UP) : null,
                                    vs.winterConsumptionKwhPer100km() != null ? vs.winterConsumptionKwhPer100km().setScale(1, RoundingMode.HALF_UP) : null,
                                    vs.totalConsumptionKwhPer100km() != null ? vs.totalConsumptionKwhPer100km().setScale(1, RoundingMode.HALF_UP) : null,
                                    vs.summerLogCount(), vs.winterLogCount());
                        }
                    }
                    return new PublicModelStatsResponse.WltpVariant(
                            e.getBatteryCapacityKwh(),
                            e.getWltpRangeKm(),
                            e.getWltpConsumptionKwhPer100km(),
                            variantConsumption,
                            variantResult.tripCount() > 0 ? variantResult.tripCount() : null,
                            variantSeasonal);
                })
                .toList();

        String displayName = brandDisplay + " " + modelDisplay;

        return Optional.of(new PublicModelStatsResponse(
                brandEnumName,
                modelEnumName,
                brandDisplay,
                displayName,
                carModel.getCategory().name(),
                carModel.getCategory().getDisplayName(),
                (int) logCount,
                uniqueContributors,
                avgCostPerKwh,
                acAvgCostPerKwh,
                dcAvgCostPerKwh,
                avgKwhPerSession,
                avgConsumption,
                communityResult.estimatedTripCount(),
                avgChargingPowerKw,
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

    /**
     * Returns all models for a brand, including those without community data.
     * Models are sorted by log count descending (popular first).
     * Returns Optional.empty() if the brand doesn't exist.
     */
    public Optional<PublicBrandResponse> getBrandModels(String brandName, boolean isSeedUser) {
        // Find the brand enum by display string (case-insensitive)
        CarBrand carBrand = null;
        for (CarBrand b : CarBrand.values()) {
            if (b.getDisplayString().equalsIgnoreCase(brandName)) {
                carBrand = b;
                break;
            }
        }
        if (carBrand == null) {
            return Optional.empty();
        }

        final CarBrand finalBrand = carBrand;
        List<CarBrand.CarModel> models = CarBrand.CarModel.byBrand(carBrand);
        if (models.isEmpty()) {
            return Optional.empty();
        }

        // Build model summaries — include all models, even those with 0 logs
        List<PublicBrandResponse.ModelSummary> summaries = models.stream()
                .map(model -> {
                    // Get community log count
                    Object[] stats = evLogRepository.findPublicBasicStatsByModel(model.name(), isSeedUser);
                    long logCount = 0;
                    if (stats != null && stats.length > 0) {
                        Object first = stats[0];
                        if (first instanceof Object[]) stats = (Object[]) first;
                        if (stats[0] != null) logCount = ((Number) stats[0]).longValue();
                    }

                    // Get community avg consumption (model-level)
                    List<Car> carsForModel = carRepository.findAllByModel(model);
                    CommunityConsumptionResult communityResult = evLogService.calculateCommunityAvgConsumption(carsForModel, isSeedUser);
                    BigDecimal avgConsumption = communityResult.value() != null
                            ? communityResult.value().setScale(1, java.math.RoundingMode.HALF_UP) : null;

                    // Build per-variant WLTP + real consumption data
                    List<VehicleSpecificationEntity> wltpEntities =
                            vehicleSpecificationRepository.findByCarModelOrderByBatteryCapacityKwhAsc(model.name());

                    List<PublicBrandResponse.WltpVariantSummary> wltpVariants = wltpEntities.stream()
                            .map(e -> {
                                List<Car> carsForVariant = carsForModel.stream()
                                        .filter(c -> c.getBatteryCapacityKwh() != null
                                                && c.getBatteryCapacityKwh().compareTo(e.getBatteryCapacityKwh()) == 0)
                                        .toList();
                                CommunityConsumptionResult variantResult = carsForVariant.isEmpty()
                                        ? CommunityConsumptionResult.EMPTY
                                        : evLogService.calculateCommunityAvgConsumption(carsForVariant, isSeedUser);
                                BigDecimal variantReal = variantResult.value() != null
                                        ? variantResult.value().setScale(1, java.math.RoundingMode.HALF_UP) : null;
                                return new PublicBrandResponse.WltpVariantSummary(
                                        e.getBatteryCapacityKwh(),
                                        e.getWltpRangeKm(),
                                        e.getWltpConsumptionKwhPer100km(),
                                        variantReal
                                );
                            })
                            .toList();

                    String urlSlug = model.getDisplayName().replace(" ", "_");

                    return new PublicBrandResponse.ModelSummary(
                            model.name(),
                            model.getDisplayName(),
                            urlSlug,
                            (int) logCount,
                            avgConsumption,
                            wltpVariants
                    );
                })
                .sorted((a, b) -> Integer.compare(b.logCount(), a.logCount()))
                .toList();

        return Optional.of(new PublicBrandResponse(
                finalBrand.name(),
                finalBrand.getDisplayString(),
                summaries
        ));
    }

    /**
     * Returns the top N models sorted by community log count.
     * Much cheaper than N individual getModelStats calls — no seasonal queries,
     * no per-variant consumption, just logCount + overall avgConsumption + WLTP lookup.
     */
    @Cacheable("topModels")
    public List<TopModelResponse> getTopModels(int limit, boolean isSeedUser) {
        record ModelData(CarBrand.CarModel carModel, long logCount,
                         BigDecimal avgConsumption, BigDecimal minRealConsumption,
                         BigDecimal maxRealConsumption, BigDecimal minWltpConsumption,
                         BigDecimal maxWltpConsumption, BigDecimal avgCostPerKwh) {}

        List<String> modelsWithWltp = vehicleSpecificationRepository.findAll().stream()
                .map(VehicleSpecificationEntity::getCarModel)
                .distinct()
                .toList();

        return modelsWithWltp.stream()
                .map(modelName -> {
                    CarBrand.CarModel carModel;
                    try {
                        carModel = CarBrand.CarModel.valueOf(modelName);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }

                    Object[] stats = evLogRepository.findPublicBasicStatsByModel(modelName, isSeedUser);
                    if (stats == null || stats.length == 0) return null;
                    Object first = stats[0];
                    if (first instanceof Object[]) stats = (Object[]) first;
                    long logCount = stats[0] != null ? ((Number) stats[0]).longValue() : 0;
                    if (logCount == 0) return null;

                    BigDecimal avgCostPerKwh = null;
                    if (stats.length > 2 && stats[2] != null) {
                        avgCostPerKwh = toBigDecimal(stats[2]).setScale(4, RoundingMode.HALF_UP);
                    }

                    List<Car> cars = carRepository.findAllByModel(carModel);
                    CommunityConsumptionResult result = evLogService.calculateCommunityAvgConsumption(cars, isSeedUser);
                    BigDecimal avgConsumption = result.value() != null
                            ? result.value().setScale(1, RoundingMode.HALF_UP) : null;

                    List<VehicleSpecificationEntity> wltpSpecs =
                            vehicleSpecificationRepository.findByCarModelOrderByBatteryCapacityKwhAsc(modelName);
                    List<BigDecimal> wltpValues = wltpSpecs.stream()
                            .map(VehicleSpecificationEntity::getWltpConsumptionKwhPer100km)
                            .filter(v -> v != null)
                            .toList();
                    BigDecimal minWltp = wltpValues.stream().min(BigDecimal::compareTo).orElse(null);
                    BigDecimal maxWltp = wltpValues.stream().max(BigDecimal::compareTo).orElse(null);

                    // Per-variant real consumption: only variants with >= 100 trips qualify
                    List<BigDecimal> variantConsumptions = wltpSpecs.stream()
                            .map(spec -> {
                                List<Car> carsForVariant = cars.stream()
                                        .filter(c -> c.getBatteryCapacityKwh() != null
                                                && c.getBatteryCapacityKwh().compareTo(spec.getBatteryCapacityKwh()) == 0)
                                        .toList();
                                if (carsForVariant.isEmpty()) return null;
                                CommunityConsumptionResult r = evLogService.calculateCommunityAvgConsumption(carsForVariant, isSeedUser);
                                return (r.tripCount() >= 100 && r.value() != null)
                                        ? r.value().setScale(1, RoundingMode.HALF_UP) : null;
                            })
                            .filter(Objects::nonNull)
                            .toList();
                    BigDecimal minReal = variantConsumptions.size() >= 2
                            ? variantConsumptions.stream().min(BigDecimal::compareTo).orElse(null) : null;
                    BigDecimal maxReal = variantConsumptions.size() >= 2
                            ? variantConsumptions.stream().max(BigDecimal::compareTo).orElse(null) : null;

                    return new ModelData(carModel, logCount, avgConsumption, minReal, maxReal, minWltp, maxWltp, avgCostPerKwh);
                })
                .filter(m -> m != null)
                .sorted((a, b) -> Long.compare(b.logCount(), a.logCount()))
                .limit(limit)
                .map(m -> {
                    String brandDisplay = m.carModel().getBrand().getDisplayString();
                    String modelDisplay = m.carModel().getDisplayName();
                    return new TopModelResponse(
                            m.carModel().getBrand().name(),
                            m.carModel().name(),
                            brandDisplay,
                            brandDisplay + " " + modelDisplay,
                            modelDisplay.replace(" ", "_"),
                            (int) m.logCount(),
                            m.avgConsumption(),
                            m.minRealConsumption(),
                            m.maxRealConsumption(),
                            m.minWltpConsumption(),
                            m.maxWltpConsumption(),
                            m.avgCostPerKwh(),
                            m.carModel().getCategory().name(),
                            m.carModel().getCategory().getDisplayName()
                    );
                })
                .toList();
    }

    /**
     * Returns the top N models sorted by lowest average real consumption (most efficient first).
     * Only includes models with avgConsumptionKwhPer100km != null and logCount >= 10.
     */
    @Cacheable("efficientModels")
    public List<TopModelResponse> getMostEfficientModels(int limit, boolean isSeedUser) {
        int MIN_LOG_COUNT = 10;
        return getTopModels(50, isSeedUser).stream()
                .filter(m -> m.avgConsumptionKwhPer100km() != null)
                .filter(m -> m.logCount() >= MIN_LOG_COUNT)
                .sorted(Comparator.comparing(TopModelResponse::avgConsumptionKwhPer100km))
                .limit(limit)
                .toList();
    }

    /**
     * Returns all vehicle categories with their display names.
     */
    public List<CategoryResponse> getCategories() {
        return Arrays.stream(VehicleCategory.values())
                .map(c -> new CategoryResponse(c.name(), c.getDisplayName()))
                .toList();
    }

    public record CategoryResponse(String key, String displayName) {}

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        if (value instanceof Float f) return BigDecimal.valueOf(f);
        return new BigDecimal(value.toString());
    }
}
