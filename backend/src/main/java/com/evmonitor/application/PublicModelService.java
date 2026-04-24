package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.VehicleCategory;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import com.evmonitor.infrastructure.persistence.JpaUserRepository;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicModelService {

    /** Minimum real community log count for a model to be included in the sitemap.
     *  Keep in sync with consumptionDataCount >= 25 in PublicModelViewV2.vue. */
    static final int SITEMAP_MIN_LOG_COUNT = 25;

    private final JpaEvLogRepository evLogRepository;
    private final JpaVehicleSpecificationRepository vehicleSpecificationRepository;
    private final JpaUserRepository userRepository;
    private final CarRepository carRepository;
    private final EvLogStatisticsService evLogStatisticsService;

    private static boolean isValidCarModelEnumName(String name) {
        if (name == null) return false;
        try {
            CarBrand.CarModel.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
        int uniqueCars = 0;
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
                if (basicStats.length > 4 && basicStats[4] != null) {
                    uniqueCars = ((Number) basicStats[4]).intValue();
                }
            } catch (Exception e) {
                log.error("Failed to parse basic stats for model {}: {}", modelEnumName, e.getMessage());
            }
        }

        // Fetch consumption via EvLogService (authoritative calculation, same logic as user stats)
        // Demo Mode: If seed user, includes ALL seed data
        List<Car> carsForModel = carRepository.findAllByModel(carModel);
        CommunityConsumptionResult communityResult = evLogStatisticsService.calculateCommunityAvgConsumption(carsForModel, isSeedUser);
        BigDecimal avgConsumption = communityResult.value() != null
                ? communityResult.value().setScale(2, RoundingMode.HALF_UP) : null;

        // Fetch seasonal distribution via EvLogService (same SoC-based logic as overall consumption)
        // Demo Mode: If seed user, includes ALL seed data
        PublicModelStatsResponse.SeasonalDistribution seasonalDistribution = null;
        SeasonalConsumptionResult seasonal = evLogStatisticsService.calculateSeasonalConsumption(carsForModel, isSeedUser);
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

        // Fetch WLTP variants (rating_source = 'WLTP' only — avoids mixing with EPA after V78 migration)
        List<VehicleSpecificationEntity> wltpEntities =
                vehicleSpecificationRepository.findByCarModelAndRatingSourceOrderByBatteryCapacityKwhAsc(modelEnumName, "WLTP");

        List<PublicModelStatsResponse.WltpVariant> wltpVariants = buildVariantStats(wltpEntities, carsForModel, carModel, isSeedUser)
                .stream()
                .map(s -> new PublicModelStatsResponse.WltpVariant(
                        s.batteryCapacityKwh(), s.variantName(), s.displayLabel(),
                        s.officialRangeKm(), s.officialRangeMinKm(),
                        s.officialConsumptionKwhPer100km(),
                        s.officialConsumptionMinKwhPer100km(), s.officialConsumptionMaxKwhPer100km(),
                        s.realConsumptionKwhPer100km(),
                        s.realConsumptionTripCount(), s.seasonalDistribution()))
                .toList();

        List<VehicleSpecificationEntity> epaEntities =
                vehicleSpecificationRepository.findByCarModelAndRatingSourceOrderByBatteryCapacityKwhAsc(modelEnumName, "EPA");

        List<PublicModelStatsResponse.EpaVariant> epaVariants = epaEntities.isEmpty() ? null :
                buildVariantStats(epaEntities, carsForModel, carModel, isSeedUser)
                        .stream()
                        .map(s -> new PublicModelStatsResponse.EpaVariant(
                                s.batteryCapacityKwh(), s.variantName(), s.displayLabel(),
                                s.officialRangeKm(),
                                s.officialConsumptionKwhPer100km(),
                                s.officialConsumptionMinKwhPer100km(), s.officialConsumptionMaxKwhPer100km(),
                                s.realConsumptionKwhPer100km(), s.realConsumptionTripCount(), s.seasonalDistribution()))
                        .toList();

        List<PublicModelStatsResponse.YearEntry> yearDistribution =
                evLogRepository.findYearDistributionByModel(modelEnumName, isSeedUser)
                        .stream()
                        .filter(row -> row[0] != null && row[1] != null)
                        .map(row -> new PublicModelStatsResponse.YearEntry(
                                ((Number) row[0]).intValue(),
                                ((Number) row[1]).intValue()))
                        .toList();

        List<PublicModelStatsResponse.RouteTypeEntry> routeTypeDistribution =
                evLogRepository.findRouteTypeDistributionByModel(modelEnumName, isSeedUser)
                        .stream()
                        .filter(row -> row[1] != null)
                        .map(row -> new PublicModelStatsResponse.RouteTypeEntry(
                                (String) row[0],
                                ((Number) row[1]).intValue()))
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
                uniqueCars,
                avgCostPerKwh,
                acAvgCostPerKwh,
                dcAvgCostPerKwh,
                avgKwhPerSession,
                avgConsumption,
                communityResult.estimatedTripCount(),
                avgChargingPowerKw,
                wltpVariants,
                epaVariants,
                seasonalDistribution,
                yearDistribution,
                routeTypeDistribution
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
        // Get all models with WLTP data.
        // Filter out rows whose car_model is not a valid CarBrand.CarModel enum name —
        // defensive guard against accidentally persisted display names like "Model 3"
        // (vs. the canonical "MODEL_3"). Such rows would otherwise crash the downstream
        // SQL query, which compares against the car_model enum column.
        List<String> modelsWithWltp = vehicleSpecificationRepository.findAll().stream()
                .map(VehicleSpecificationEntity::getCarModel)
                .distinct()
                .filter(PublicModelService::isValidCarModelEnumName)
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
     * Returns enum names of models with at least SITEMAP_MIN_LOG_COUNT real community logs.
     * Used by the sitemap to exclude thin-content model pages from crawling.
     * Iterates all CarModel enum values (no WLTP dependency) and checks log counts in batch.
     */
    public Set<String> getModelEnumNamesForSitemap(boolean isSeedUser) {
        return Arrays.stream(CarBrand.CarModel.values())
                .filter(model -> model.getBrand() != CarBrand.SONSTIGE)
                .filter(model -> {
                    Object[] stats = evLogRepository.findPublicBasicStatsByModel(model.name(), isSeedUser);
                    if (stats == null || stats.length == 0) return false;
                    Object first = stats[0];
                    if (first instanceof Object[]) stats = (Object[]) first;
                    long count = stats[0] != null ? ((Number) stats[0]).longValue() : 0;
                    return count >= SITEMAP_MIN_LOG_COUNT;
                })
                .map(CarBrand.CarModel::name)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all models for a brand, including those without community data.
     * Models are sorted by log count descending (popular first).
     * Returns Optional.empty() if the brand doesn't exist.
     */
    @Cacheable("brandModels")
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
                    CommunityConsumptionResult communityResult = evLogStatisticsService.calculateCommunityAvgConsumption(carsForModel, isSeedUser);
                    BigDecimal avgConsumption = communityResult.value() != null
                            ? communityResult.value().setScale(1, java.math.RoundingMode.HALF_UP) : null;

                    List<VehicleSpecificationEntity> wltpEntities =
                            vehicleSpecificationRepository.findByCarModelOrderByBatteryCapacityKwhAsc(model.name());

                    List<PublicBrandResponse.WltpVariantSummary> wltpVariants =
                            buildVariantStats(wltpEntities, carsForModel, model, isSeedUser).stream()
                                    .map(s -> new PublicBrandResponse.WltpVariantSummary(
                                            s.batteryCapacityKwh(), s.variantName(), s.displayLabel(),
                                            s.realConsumptionKwhPer100km(),
                                            s.realConsumptionMinKwhPer100km(),
                                            s.realConsumptionMaxKwhPer100km()))
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
                    CommunityConsumptionResult result = evLogStatisticsService.calculateCommunityAvgConsumption(cars, isSeedUser);
                    BigDecimal avgConsumption = result.value() != null
                            ? result.value().setScale(1, RoundingMode.HALF_UP) : null;

                    List<VehicleSpecificationEntity> wltpSpecs =
                            vehicleSpecificationRepository.findByCarModelAndRatingSourceOrderByBatteryCapacityKwhAsc(modelName, "WLTP");
                    List<BigDecimal> wltpValues = wltpSpecs.stream()
                            .map(VehicleSpecificationEntity::getOfficialConsumptionKwhPer100km)
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
                                CommunityConsumptionResult r = evLogStatisticsService.calculateCommunityAvgConsumption(carsForVariant, isSeedUser);
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

    private static final int MIN_TRIPS_FOR_REAL_RANGE = 5;

    /** Intermediate shape used by buildVariantStats — source-agnostic. */
    private record VariantStats(
            BigDecimal batteryCapacityKwh,
            String variantName,
            String displayLabel,
            BigDecimal officialRangeKm,          // best (max) range across group
            BigDecimal officialRangeMinKm,        // null if single-spec or all same
            BigDecimal officialConsumptionKwhPer100km,    // average across group (fair delta baseline)
            BigDecimal officialConsumptionMinKwhPer100km, // null if single-spec
            BigDecimal officialConsumptionMaxKwhPer100km,
            BigDecimal realConsumptionKwhPer100km,
            BigDecimal realConsumptionMinKwhPer100km,     // null if no per-spec range
            BigDecimal realConsumptionMaxKwhPer100km,
            Integer realConsumptionTripCount,
            PublicModelStatsResponse.SeasonalDistribution seasonalDistribution
    ) {}

    /** Groups spec entities by trimLevel. Specs without trimLevel each become a solo group. */
    record TrimGroup(String trimLevel, List<VehicleSpecificationEntity> specs) {}

    List<TrimGroup> groupByTrimLevel(List<VehicleSpecificationEntity> entities) {
        boolean anyHasTrimLevel = entities.stream().anyMatch(e -> e.getTrimLevel() != null);
        if (!anyHasTrimLevel) {
            return entities.stream().map(e -> new TrimGroup(null, List.of(e))).toList();
        }
        Map<String, List<VehicleSpecificationEntity>> grouped = new LinkedHashMap<>();
        for (VehicleSpecificationEntity e : entities) {
            String key = e.getTrimLevel() != null ? e.getTrimLevel() : "solo_" + e.getId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        return grouped.entrySet().stream()
                .map(entry -> new TrimGroup(
                        entry.getKey().startsWith("solo_") ? null : entry.getKey(),
                        entry.getValue()))
                .toList();
    }

    /**
     * Compute per-variant community consumption + seasonal data for a list of spec entities.
     * Groups by trimLevel when present (e.g. all Model 3 LR AWD rows → one "Long Range AWD" entry).
     * Falls back to per-entity rows for brands without trimLevel data.
     */
    private List<VariantStats> buildVariantStats(
            List<VehicleSpecificationEntity> entities,
            List<Car> carsForModel,
            CarBrand.CarModel carModel,
            boolean isSeedUser) {

        return groupByTrimLevel(entities).stream().map(group -> {
            Set<UUID> groupSpecIds = group.specs().stream()
                    .map(VehicleSpecificationEntity::getId).collect(Collectors.toSet());
            Set<BigDecimal> groupKwh = group.specs().stream()
                    .map(VehicleSpecificationEntity::getBatteryCapacityKwh).collect(Collectors.toSet());

            List<Car> carsForVariant = carsForModel.stream()
                    .filter(c -> {
                        if (c.getVehicleSpecificationId() != null)
                            return groupSpecIds.contains(c.getVehicleSpecificationId());
                        return c.getBatteryCapacityKwh() != null && groupKwh.contains(c.getBatteryCapacityKwh());
                    })
                    .toList();

            // WLTP: average as delta baseline, min/max for range display
            List<BigDecimal> wltpConsumptions = group.specs().stream()
                    .map(VehicleSpecificationEntity::getOfficialConsumptionKwhPer100km)
                    .filter(Objects::nonNull).toList();
            BigDecimal wltpMin = wltpConsumptions.stream().min(BigDecimal::compareTo).orElse(null);
            BigDecimal wltpMax = wltpConsumptions.stream().max(BigDecimal::compareTo).orElse(null);
            BigDecimal wltpAvg = wltpConsumptions.isEmpty() ? null :
                    wltpConsumptions.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(wltpConsumptions.size()), 2, RoundingMode.HALF_UP);
            boolean hasWltpRange = wltpMin != null && wltpMax != null && wltpMin.compareTo(wltpMax) != 0;

            // WLTP range km: best (max) for hero display, min for range display
            List<BigDecimal> rangeKms = group.specs().stream()
                    .map(VehicleSpecificationEntity::getOfficialRangeKm)
                    .filter(Objects::nonNull).toList();
            BigDecimal bestRangeKm = rangeKms.stream().max(BigDecimal::compareTo).orElse(null);
            BigDecimal minRangeKm  = rangeKms.stream().min(BigDecimal::compareTo).orElse(null);
            boolean hasRangeRange = minRangeKm != null && bestRangeKm != null && minRangeKm.compareTo(bestRangeKm) != 0;

            // Representative net capacity for range math
            BigDecimal representativeKwh = group.specs().stream()
                    .map(e -> e.getNetBatteryCapacityKwh() != null ? e.getNetBatteryCapacityKwh() : e.getBatteryCapacityKwh())
                    .filter(Objects::nonNull).max(BigDecimal::compareTo)
                    .orElse(group.specs().get(0).getBatteryCapacityKwh());

            String displayLabel = group.trimLevel();
            String variantName  = group.trimLevel() == null
                    ? carModel.variantNameFor(group.specs().get(0).getBatteryCapacityKwh()).orElse(
                            group.specs().get(0).getVariantName())
                    : null;

            CommunityConsumptionResult variantResult = carsForVariant.isEmpty()
                    ? CommunityConsumptionResult.EMPTY
                    : evLogStatisticsService.calculateCommunityAvgConsumption(carsForVariant, isSeedUser);
            BigDecimal variantConsumption = variantResult.value() != null
                    ? variantResult.value().setScale(1, RoundingMode.HALF_UP) : null;

            // Per-spec real consumption for range display
            List<BigDecimal> perSpecConsumptions = group.specs().stream()
                    .map(spec -> {
                        List<Car> carsForSpec = carsForVariant.stream()
                                .filter(c -> c.getVehicleSpecificationId() != null
                                        ? spec.getId().equals(c.getVehicleSpecificationId())
                                        : c.getBatteryCapacityKwh() != null && c.getBatteryCapacityKwh().compareTo(spec.getBatteryCapacityKwh()) == 0)
                                .toList();
                        if (carsForSpec.isEmpty()) return null;
                        CommunityConsumptionResult r = evLogStatisticsService.calculateCommunityAvgConsumption(carsForSpec, isSeedUser);
                        return (r.tripCount() >= MIN_TRIPS_FOR_REAL_RANGE && r.value() != null)
                                ? r.value().setScale(1, RoundingMode.HALF_UP) : null;
                    })
                    .filter(Objects::nonNull).distinct().toList();
            BigDecimal realConsMin = perSpecConsumptions.size() >= 2
                    ? perSpecConsumptions.stream().min(BigDecimal::compareTo).orElse(null) : null;
            BigDecimal realConsMax = perSpecConsumptions.size() >= 2
                    ? perSpecConsumptions.stream().max(BigDecimal::compareTo).orElse(null) : null;
            boolean hasRealRange = realConsMin != null && realConsMax != null && realConsMin.compareTo(realConsMax) != 0;

            PublicModelStatsResponse.SeasonalDistribution variantSeasonal = null;
            if (!carsForVariant.isEmpty()) {
                SeasonalConsumptionResult vs = evLogStatisticsService.calculateSeasonalConsumption(carsForVariant, isSeedUser);
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

            return new VariantStats(
                    representativeKwh, variantName, displayLabel,
                    bestRangeKm, hasRangeRange ? minRangeKm : null,
                    wltpAvg,
                    hasWltpRange ? wltpMin : null, hasWltpRange ? wltpMax : null,
                    variantConsumption,
                    hasRealRange ? realConsMin : null, hasRealRange ? realConsMax : null,
                    variantResult.tripCount() > 0 ? variantResult.tripCount() : null,
                    variantSeasonal);
        }).toList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        if (value instanceof Float f) return BigDecimal.valueOf(f);
        return new BigDecimal(value.toString());
    }
}
