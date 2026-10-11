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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Wiring of the basis: which community figures feed the calculator, and the result mail. */
class PersonalRangeServiceTest {

    private final PublicModelService publicModelService = mock(PublicModelService.class);
    private final JpaVehicleSpecificationRepository specs = mock(JpaVehicleSpecificationRepository.class);
    private final ChargingPriceService prices = mock(ChargingPriceService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final PersonalRangeService service = new PersonalRangeService(publicModelService, specs, prices,
            userRepository, new PlausibilityProperties(), new ObjectMapper(), emailService);

    private final UUID userId = UUID.randomUUID();
    private final PersonalRangeRequest request = new PersonalRangeRequest(40, 30, true, 500);

    @BeforeEach
    void setUp() {
        VehicleSpecificationEntity spec = mock(VehicleSpecificationEntity.class);
        when(spec.getBatteryCapacityKwh()).thenReturn(new BigDecimal("82"));
        when(spec.getNetBatteryCapacityKwh()).thenReturn(new BigDecimal("75"));
        when(spec.getOfficialConsumptionKwhPer100km()).thenReturn(new BigDecimal("14.7"));
        when(specs.findByCarModelAndRatingSourceOrderByBatteryCapacityKwhAsc(anyString(), eq("WLTP"))).thenReturn(List.of(spec));
        ChargingReferencePrices reference = mock(ChargingReferencePrices.class);
        when(reference.homePricePerKwh()).thenReturn(new BigDecimal("0.30"));
        when(reference.publicPricePerKwh()).thenReturn(new BigDecimal("0.50"));
        when(prices.getReferencePrices(anyBoolean())).thenReturn(reference);

        PublicModelStatsResponse stats = mock(PublicModelStatsResponse.class);
        when(stats.model()).thenReturn("MODEL_3");
        when(stats.modelDisplayName()).thenReturn("Tesla Model 3");
        when(stats.avgConsumptionKwhPer100km()).thenReturn(new BigDecimal("18.1"));
        when(stats.avgChargingPowerKw()).thenReturn(new BigDecimal("41.1"));
        when(stats.routeTypeConsumption()).thenReturn(new PublicModelStatsResponse.RouteTypeConsumption(
                new BigDecimal("23.9"), new BigDecimal("20.0"), new BigDecimal("21.7"), 50, 300, 120, 20));
        when(publicModelService.getModelStats(eq("Tesla"), eq("Model_3"), anyBoolean())).thenReturn(Optional.of(stats));

        TopModelResponse top = mock(TopModelResponse.class);
        when(top.model()).thenReturn("MODEL_3");
        when(top.fastChargePowerKw()).thenReturn(new BigDecimal("130"));
        when(publicModelService.getTopModels(anyBoolean())).thenReturn(List.of(top));
    }

    @Test
    void basisUsesModelAverageAndFastChargePowerNotCityAndSessionMean() {
        PersonalRangeResponse r = service.calculate(userId, false, "Tesla", "Model_3", request).orElseThrow();

        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("BASE_CONSUMPTION")
                && a.value().compareTo(new BigDecimal("18.1")) == 0 && a.measured());
        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("CHARGING_POWER")
                && a.value().compareTo(new BigDecimal("130")) == 0 && a.measured());
        // 75 kWh × 0,7 / 130 kW = 24 min je Stopp (auf 5 gerundet 25), nicht 77 min wie beim Session-Mittel
        assertThat(r.longTripChargingMinutes()).isEqualTo(25);
    }

    @Test
    void withoutFastChargeFigureThePowerIsTheFixedEstimate() {
        when(publicModelService.getTopModels(anyBoolean())).thenReturn(List.of());

        PersonalRangeResponse r = service.calculate(userId, false, "Tesla", "Model_3", request).orElseThrow();

        assertThat(r.assumptions()).anyMatch(a -> a.key().equals("CHARGING_POWER")
                && a.value().compareTo(PersonalRangeCalculator.ASSUMED_DC_POWER_KW) == 0 && !a.measured());
    }

    @Test
    void emailResultSendsInputsAndResultToTheUserWithModelNameAndPath() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("ihle@example.com");
        when(user.getRegistrationLocale()).thenReturn("de");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        PersonalRangeResponse result = service.calculate(userId, false, "Tesla", "Model_3", request).orElseThrow();
        var content = new EmailService.PersonalRangeMailContent(request, result);

        service.emailResult(userId, "Tesla", "Model_3", content);

        verify(emailService).sendPersonalRangeResultEmail("ihle@example.com", "de", "Tesla Model 3",
                "/modelle/Tesla/Model_3", content);
    }

    @Test
    void previewReturnsWinterToSummerBandWithoutSavingOrMailing() {
        PersonalRangeResponse full = service.calculate(userId, false, "Tesla", "Model_3", request).orElseThrow();

        PersonalRangePreviewResponse p = service.preview("Tesla", "Model_3", request, false).orElseThrow();

        assertThat(p.minKm()).isEqualTo(full.winterRangeKm());
        assertThat(p.maxKm()).isEqualTo(full.summerRangeKm());
        // only the two season values decide the level of the band, not highway or base
        assertThat(p.dataLevel()).isEqualTo(PersonalRangeResponse.DataLevel.WLTP_FACTOR);
        verify(userRepository, times(1)).setRangeProfile(any(), any());
        verify(emailService, never()).sendPersonalRangeResultEmail(any(), any(), any(), any(), any());
    }

    @Test
    void previewUsesTheBatteryVariantThePageShowsWhenGiven() {
        PersonalRangeRequest small = new PersonalRangeRequest(40, 30, true, 500, null, new BigDecimal("58"));

        PersonalRangePreviewResponse p = service.preview("Tesla", "Model_3", small, false).orElseThrow();
        PersonalRangePreviewResponse large = service.preview("Tesla", "Model_3", request, false).orElseThrow();

        // 58 instead of the spec's 75 kWh: both ends of the band shrink by the same ratio (rounded to 10)
        assertThat(p.maxKm()).isLessThan(large.maxKm());
        assertThat(p.minKm()).isLessThan(large.minKm());
    }

    @Test
    void previewOfUnknownModelIsEmpty() {
        assertThat(service.preview("Foo", "Bar", request, false)).isEmpty();
    }

    @Test
    void emailResultForUnknownUserSendsNothing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.emailResult(userId, "Tesla", "Model_3", mock(EmailService.PersonalRangeMailContent.class));

        verify(emailService, never()).sendPersonalRangeResultEmail(any(), any(), any(), any(), any());
    }
}
