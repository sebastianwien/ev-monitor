package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Builds the partner tile from the cached public model stats. No own consumption maths:
 * every consumption value comes from PublicModelService (which delegates to EvLogService),
 * this class only turns the model average into range figures.
 */
@Service
public class PublicEmbedService {

    /** Usable share of the net battery for range figures (90 → 0 %), same as the model page. */
    private static final BigDecimal USABLE_SHARE = new BigDecimal("0.9");
    /** Same threshold as the seasonal card on the model page. */
    private static final int MIN_LOGS_PER_SEASON = 10;

    private final PublicModelService publicModelService;
    private final BigDecimal dcChargingEfficiency;
    private final String baseUrl;

    public PublicEmbedService(PublicModelService publicModelService, PlausibilityProperties plausibility,
                              @Value("${app.base-url:http://localhost:5173}") String baseUrl) {
        this.publicModelService = publicModelService;
        this.dcChargingEfficiency = BigDecimal.valueOf(plausibility.getDcChargingEfficiency());
        this.baseUrl = baseUrl;
    }

    public ModelTileResponse getModelTile(String brand, String model) {
        return publicModelService.getModelStats(brand, model, false)
                .map(this::toTile)
                .orElseGet(ModelTileResponse::unknown);
    }

    private ModelTileResponse toTile(PublicModelStatsResponse stats) {
        String modelUrl = modelUrl(stats);
        List<PublicModelStatsResponse.WltpVariant> variants = stats.wltpVariants() == null ? List.of() : stats.wltpVariants();

        // Consumption: the model average, the same figure as the page title and meta description
        // (the tile's subline counts the whole model). Battery and seasons: the variant with the
        // most measured trips, the one the model page opens with, so the range matches its hero.
        BigDecimal consumption = stats.avgConsumptionKwhPer100km();
        PublicModelStatsResponse.WltpVariant representative = variants.stream()
                .filter(v -> v.realConsumptionKwhPer100km() != null)
                .max(Comparator.comparing(v -> v.realConsumptionTripCount() == null ? 0 : v.realConsumptionTripCount()))
                .orElse(null);
        BigDecimal netKwh = representative != null ? representative.batteryCapacityKwh()
                : variants.stream().map(PublicModelStatsResponse.WltpVariant::batteryCapacityKwh)
                        .filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);

        if (consumption == null || consumption.signum() <= 0) {
            return new ModelTileResponse(false, stats.modelDisplayName(), null, null, null, null,
                    stats.logCount(), stats.uniqueCars(), modelUrl, null, null, null, null, null, null);
        }

        PublicModelStatsResponse.SeasonalDistribution seasonal = representative != null && representative.seasonalDistribution() != null
                ? representative.seasonalDistribution() : stats.seasonalDistribution();
        boolean seasonalReliable = reliable(seasonal);
        // Seasonal consumption: model-wide like the average and the log count the tile shows next to it
        PublicModelStatsResponse.SeasonalDistribution modelSeasonal = reliable(stats.seasonalDistribution())
                ? stats.seasonalDistribution() : null;
        BigDecimal dcPrice = stats.dcAvgCostPerKwh() != null ? stats.dcAvgCostPerKwh() : stats.avgCostPerKwh();

        return new ModelTileResponse(
                true,
                stats.modelDisplayName(),
                consumption.setScale(1, RoundingMode.HALF_UP),
                rangeKm(netKwh, consumption),
                seasonalReliable ? rangeKm(netKwh, seasonal.summerConsumptionKwhPer100km()) : null,
                seasonalReliable ? rangeKm(netKwh, seasonal.winterConsumptionKwhPer100km()) : null,
                stats.logCount(),
                stats.uniqueCars(),
                modelUrl,
                modelSeasonal != null ? modelSeasonal.summerConsumptionKwhPer100km() : null,
                modelSeasonal != null ? modelSeasonal.winterConsumptionKwhPer100km() : null,
                modelSeasonal != null ? modelSeasonal.summerLogCount() : null,
                modelSeasonal != null ? modelSeasonal.winterLogCount() : null,
                typicalChargeCost(stats),
                FastChargeStop.cost(netKwh, dcChargingEfficiency, dcPrice));
    }

    private static boolean reliable(PublicModelStatsResponse.SeasonalDistribution seasonal) {
        return seasonal != null
                && seasonal.summerLogCount() >= MIN_LOGS_PER_SEASON
                && seasonal.winterLogCount() >= MIN_LOGS_PER_SEASON;
    }

    /** Average charge of the model at the plug (kWh and price both at the charger, losses included). */
    private static BigDecimal typicalChargeCost(PublicModelStatsResponse stats) {
        if (stats.avgKwhPerSession() == null || stats.avgCostPerKwh() == null) return null;
        return stats.avgKwhPerSession().multiply(stats.avgCostPerKwh()).setScale(2, RoundingMode.HALF_UP);
    }

    private String modelUrl(PublicModelStatsResponse stats) {
        CarBrand.CarModel carModel = CarBrand.CarModel.valueOf(stats.model());
        return baseUrl + "/modelle/" + carModel.getBrand().getDisplayString()
                + "/" + carModel.getDisplayName().replace(" ", "_");
    }

    /** Net battery × 0.9 / consumption, rounded to 10 km; null if either input is missing. */
    private static Integer rangeKm(BigDecimal netKwh, BigDecimal consumptionKwhPer100km) {
        if (netKwh == null || consumptionKwhPer100km == null || consumptionKwhPer100km.signum() <= 0) return null;
        BigDecimal km = netKwh.multiply(USABLE_SHARE).multiply(BigDecimal.valueOf(100))
                .divide(consumptionKwhPer100km, 0, RoundingMode.HALF_UP);
        return km.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).intValue() * 10;
    }
}
