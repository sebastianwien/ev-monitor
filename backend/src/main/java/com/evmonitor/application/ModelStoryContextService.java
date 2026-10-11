package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Fleet values for the model page, read from the cached ranking so the page does not have to load
 * the whole ranking for two numbers. Lives outside PublicModelService: a call from inside that class
 * would bypass the ranking cache.
 */
@Service
@RequiredArgsConstructor
public class ModelStoryContextService {

    static final int MIN_FLEET_MODELS = 3;

    private final PublicModelService publicModelService;

    /** Empty for an unknown model. */
    public Optional<ModelStoryContext> context(String brand, String model, boolean isSeedUser) {
        return CarBrand.CarModel.findBySlug(brand, model)
                .filter(m -> m.getBrand() != CarBrand.SONSTIGE)
                .map(carModel -> {
                    List<TopModelResponse> ranking = publicModelService.getTopModels(isSeedUser);
                    Optional<TopModelResponse> own = ranking.stream()
                            .filter(m -> carModel.name().equals(m.model()))
                            .findFirst();
                    List<BigDecimal> ratios = ranking.stream()
                            .filter(m -> m.summerConsumptionKwhPer100km() != null && m.winterConsumptionKwhPer100km() != null
                                    && m.summerConsumptionKwhPer100km().signum() > 0)
                            .map(m -> m.winterConsumptionKwhPer100km().divide(m.summerConsumptionKwhPer100km(), 4, RoundingMode.HALF_UP))
                            .sorted()
                            .toList();
                    return new ModelStoryContext(
                            own.map(TopModelResponse::fastChargePowerKw).orElse(null), median(ratios), ratios.size(),
                            own.map(TopModelResponse::minRealConsumptionKwhPer100km).orElse(null),
                            own.map(TopModelResponse::maxRealConsumptionKwhPer100km).orElse(null));
                });
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        if (sorted.size() < MIN_FLEET_MODELS) return null;
        int mid = sorted.size() / 2;
        BigDecimal median = sorted.size() % 2 == 1 ? sorted.get(mid)
                : sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        return median.setScale(2, RoundingMode.HALF_UP);
    }
}
