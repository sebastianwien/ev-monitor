package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class PublicModelService {

    private final JpaEvLogRepository evLogRepository;
    private final JpaVehicleSpecificationRepository vehicleSpecificationRepository;

    public PublicModelService(JpaEvLogRepository evLogRepository,
                              JpaVehicleSpecificationRepository vehicleSpecificationRepository) {
        this.evLogRepository = evLogRepository;
        this.vehicleSpecificationRepository = vehicleSpecificationRepository;
    }

    /**
     * Returns public, anonymized statistics for a car model.
     * Excludes all data from seed/test users.
     * Returns Optional.empty() if the model enum doesn't exist.
     */
    public Optional<PublicModelStatsResponse> getModelStats(String brandName, String modelName) {
        // Validate that the model actually exists in our enum
        CarBrand.CarModel carModel;
        try {
            carModel = CarBrand.CarModel.valueOf(modelName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String modelEnumName = carModel.name();
        String brandEnumName = carModel.getBrand().name();

        // Fetch basic aggregated stats (count, cost, kwh)
        Object[] basicStats = evLogRepository.findPublicBasicStatsByModel(modelEnumName);

        long logCount = 0;
        int uniqueContributors = 0;
        BigDecimal avgCostPerKwh = null;
        BigDecimal avgKwhPerSession = null;

        if (basicStats != null && basicStats[0] != null) {
            logCount = ((Number) basicStats[0]).longValue();
            uniqueContributors = ((Number) basicStats[1]).intValue();
            if (basicStats[2] != null) {
                avgCostPerKwh = toBigDecimal(basicStats[2]).setScale(4, RoundingMode.HALF_UP);
            }
            if (basicStats[3] != null) {
                avgKwhPerSession = toBigDecimal(basicStats[3]).setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Fetch consumption (may be null if no odometer data)
        BigDecimal avgConsumption = evLogRepository.findAvgConsumptionByModel(modelEnumName);
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

        String displayName = buildDisplayName(brandEnumName, modelEnumName);

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
     * Returns a list of all models that have at least one WLTP entry,
     * suitable for a sitemap / model index page.
     */
    public List<String> getModelsWithWltpData() {
        return vehicleSpecificationRepository.findAll().stream()
                .map(VehicleSpecificationEntity::getCarModel)
                .distinct()
                .sorted()
                .toList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        if (value instanceof Float f) return BigDecimal.valueOf(f);
        return new BigDecimal(value.toString());
    }

    /**
     * Converts enum names like TESLA / MODEL_3 to "Tesla Model 3".
     */
    private String buildDisplayName(String brand, String model) {
        String brandDisplay = toTitleCase(brand.replace("_", " "));
        String modelDisplay = toTitleCase(model.replace("_", " "));
        return brandDisplay + " " + modelDisplay;
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        return Arrays.stream(input.split(" "))
                .map(word -> word.isEmpty() ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(input);
    }
}
