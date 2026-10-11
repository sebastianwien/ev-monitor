package com.evmonitor.application.personalrange;

import com.evmonitor.application.ChargingPriceService;
import com.evmonitor.application.ChargingReferencePrices;
import com.evmonitor.application.PlausibilityProperties;
import com.evmonitor.application.PublicModelService;
import com.evmonitor.application.PublicModelStatsResponse;
import com.evmonitor.application.TopModelResponse;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.persistence.JpaVehicleSpecificationRepository;
import com.evmonitor.infrastructure.persistence.VehicleSpecificationEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
    private final PlausibilityProperties plausibility;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    /**
     * Mails the result the user just unlocked through the gate. Runs off the request thread, so the
     * SMTP round trip never delays the response; a failing mail is only logged.
     */
    @Async
    public void emailResult(UUID userId, String brand, String model, EmailService.PersonalRangeMailContent content) {
        Optional<User> user = userRepository.findById(userId);
        Optional<PublicModelStatsResponse> stats = publicModelService.getModelStats(brand, model,
                user.map(User::isSeedData).orElse(false));
        if (user.isEmpty() || stats.isEmpty()) return;
        try {
            emailService.sendPersonalRangeResultEmail(user.get().getEmail(), user.get().getRegistrationLocale(),
                    stats.get().modelDisplayName(), "/modelle/" + brand + "/" + model, content);
        } catch (RuntimeException e) {
            log.warn("Could not mail the range result to user {}: {}", userId, e.getMessage());
        }
    }

    /** Empty if the model is unknown or has no WLTP spec to size the battery. */
    public Optional<PersonalRangeResponse> calculate(UUID userId, boolean isSeedUser, String brand, String model,
                                                     PersonalRangeRequest request) {
        Optional<PersonalRangeResponse> result = publicModelService.getModelStats(brand, model, isSeedUser)
                .flatMap(stats -> basis(stats, isSeedUser, request))
                .map(basis -> PersonalRangeCalculator.calculate(request, basis));
        result.ifPresent(r -> saveProfile(userId, request));
        return result;
    }

    /**
     * The range band for the visitor before the gate: same basis as calculate(), but nothing is stored
     * and nothing is mailed. Empty if the model is unknown or has no WLTP spec.
     */
    public Optional<PersonalRangePreviewResponse> preview(String brand, String model, PersonalRangeRequest request,
                                                          boolean isSeedUser) {
        return publicModelService.getModelStats(brand, model, isSeedUser)
                .flatMap(stats -> basis(stats, isSeedUser, request))
                .map(basis -> PersonalRangeCalculator.preview(request, basis));
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
            userRepository.setRangeProfile(userId, objectMapper.writeValueAsString(request.withoutBattery()));
        } catch (JsonProcessingException e) {
            log.warn("Could not store range profile for user {}", userId);
        }
    }

    /** The battery is the variant the page shows when the request names one, else the largest WLTP variant. */
    private Optional<ModelRangeBasis> basis(PublicModelStatsResponse stats, boolean isSeedUser, PersonalRangeRequest request) {
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

        boolean netKnown = request.netBatteryKwh() != null || largest.getNetBatteryCapacityKwh() != null;
        BigDecimal net = request.netBatteryKwh() != null ? request.netBatteryKwh()
                : largest.getNetBatteryCapacityKwh() != null ? largest.getNetBatteryCapacityKwh()
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
                .highwayCarCount(route != null ? route.highwayCarCount() : 0)
                // Model average, the figure the page shows. The city value sits on too few logs and
                // came out above the highway value for several models.
                .baseKwhPer100km(stats.avgConsumptionKwhPer100km())
                .routeMeanKwhPer100km(route != null ? route.typedMeanKwhPer100km() : null)
                // Short-stop fast-charge power as in the model ranking, not the session mean
                // (that one includes the taper and idle time and halves the real figure)
                .dcChargingPowerKw(fastChargePowerKw(stats.model(), isSeedUser))
                .homePricePerKwh(stats.acAvgCostPerKwh())
                .publicPricePerKwh(stats.dcAvgCostPerKwh())
                .referenceHomePricePerKwh(reference.homePricePerKwh())
                .referencePublicPricePerKwh(reference.publicPricePerKwh())
                .homeChargingEfficiency(BigDecimal.valueOf(plausibility.getAcChargingEfficiency()))
                .publicChargingEfficiency(BigDecimal.valueOf(plausibility.getDcChargingEfficiency()))
                .fleetFactors(fleetFactors(isSeedUser))
                .build());
    }

    /** From the cached top-models list, null when the model is not listed or has too few DC sessions. */
    private BigDecimal fastChargePowerKw(String modelEnumName, boolean isSeedUser) {
        return publicModelService.getTopModels(isSeedUser).stream()
                .filter(m -> modelEnumName.equals(m.model()))
                .map(TopModelResponse::fastChargePowerKw)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /** From the cached top-models list: per model WLTP, overall and seasonal community consumption. */
    private WltpFleetFactors fleetFactors(boolean isSeedUser) {
        return WltpFleetFactors.from(publicModelService.getTopModels(isSeedUser).stream()
                .map(m -> new WltpFleetFactors.Sample(m.carCount() != null ? m.carCount() : 0,
                        m.avgWltpConsumptionKwhPer100km(), m.avgConsumptionKwhPer100km(),
                        m.summerConsumptionKwhPer100km(), m.winterConsumptionKwhPer100km()))
                .toList());
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
