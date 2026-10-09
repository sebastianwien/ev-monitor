package com.evmonitor.application.personalrange;

import com.evmonitor.application.ChargingPriceService;
import com.evmonitor.application.ChargingReferencePrices;
import com.evmonitor.application.PublicModelService;
import com.evmonitor.application.PublicModelStatsResponse;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Personal range calculator for logged-in users. Collects the model basis from the cached public
 * model stats (consumption values from EvLogService), the WLTP spec of the largest variant and the
 * reference prices, then hands off to PersonalRangeCalculator. Stores the inputs as the user's profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalRangeService {

    /** Same threshold as the seasonal card on the model page. */
    private static final int MIN_LOGS_PER_SEASON = 10;
    private static final BigDecimal NOMINAL_TO_NET = new BigDecimal("0.9");

    private final PublicModelService publicModelService;
    private final JpaVehicleSpecificationRepository vehicleSpecificationRepository;
    private final ChargingPriceService chargingPriceService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /** Empty if the model is unknown or has no WLTP spec to size the battery. */
    public Optional<PersonalRangeResponse> calculate(UUID userId, boolean isSeedUser, String brand, String model,
                                                     PersonalRangeRequest request) {
        Optional<PersonalRangeResponse> result = publicModelService.getModelStats(brand, model, isSeedUser)
                .flatMap(stats -> basis(stats, isSeedUser))
                .map(basis -> PersonalRangeCalculator.calculate(request, basis));
        result.ifPresent(r -> saveProfile(userId, request));
        return result;
    }

    public Optional<PersonalRangeRequest> getProfile(UUID userId) {
        return userRepository.findRangeProfile(userId).flatMap(json -> {
            try {
                return Optional.of(objectMapper.readValue(json, PersonalRangeRequest.class));
            } catch (JsonProcessingException e) {
                log.warn("Unreadable range profile for user {}", userId);
                return Optional.empty();
            }
        });
    }

    private void saveProfile(UUID userId, PersonalRangeRequest request) {
        try {
            userRepository.setRangeProfile(userId, objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            log.warn("Could not store range profile for user {}", userId);
        }
    }

    private Optional<ModelRangeBasis> basis(PublicModelStatsResponse stats, boolean isSeedUser) {
        List<VehicleSpecificationEntity> specs =
                vehicleSpecificationRepository.findByCarModelAndRatingSourceOrderByBatteryCapacityKwhAsc(stats.model(), "WLTP");
        VehicleSpecificationEntity largest = specs.stream()
                .filter(s -> s.getBatteryCapacityKwh() != null)
                .max(Comparator.comparing(VehicleSpecificationEntity::getBatteryCapacityKwh))
                .orElse(null);
        BigDecimal wltp = largest != null && largest.getOfficialConsumptionKwhPer100km() != null
                ? largest.getOfficialConsumptionKwhPer100km()
                : averageWltp(specs);
        if (largest == null || wltp == null) return Optional.empty();

        boolean netKnown = largest.getNetBatteryCapacityKwh() != null;
        BigDecimal net = netKnown ? largest.getNetBatteryCapacityKwh()
                : largest.getBatteryCapacityKwh().multiply(NOMINAL_TO_NET).setScale(1, RoundingMode.HALF_UP);

        PublicModelStatsResponse.SeasonalDistribution seasonal = stats.seasonalDistribution();
        PublicModelStatsResponse.RouteTypeConsumption route = stats.routeTypeConsumption();
        ChargingReferencePrices reference = chargingPriceService.getReferencePrices(isSeedUser);

        return Optional.of(ModelRangeBasis.builder()
                .netBatteryKwh(net)
                .netBatteryMeasured(netKnown)
                .wltpKwhPer100km(wltp)
                .summerKwhPer100km(seasonal != null && seasonal.summerLogCount() >= MIN_LOGS_PER_SEASON
                        ? seasonal.summerConsumptionKwhPer100km() : null)
                .winterKwhPer100km(seasonal != null && seasonal.winterLogCount() >= MIN_LOGS_PER_SEASON
                        ? seasonal.winterConsumptionKwhPer100km() : null)
                .highwayKwhPer100km(route != null ? route.highwayKwhPer100km() : null)
                .baseKwhPer100km(route != null && route.cityKwhPer100km() != null
                        ? route.cityKwhPer100km() : stats.avgConsumptionKwhPer100km())
                .dcChargingPowerKw(stats.avgChargingPowerKw())
                .homePricePerKwh(stats.acAvgCostPerKwh())
                .publicPricePerKwh(stats.dcAvgCostPerKwh())
                .referenceHomePricePerKwh(reference.homePricePerKwh())
                .referencePublicPricePerKwh(reference.publicPricePerKwh())
                .build());
    }

    private static BigDecimal averageWltp(List<VehicleSpecificationEntity> specs) {
        List<BigDecimal> values = specs.stream()
                .map(VehicleSpecificationEntity::getOfficialConsumptionKwhPer100km)
                .filter(Objects::nonNull).toList();
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }
}
