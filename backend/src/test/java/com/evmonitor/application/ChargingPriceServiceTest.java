package com.evmonitor.application;

import java.util.List;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.infrastructure.external.FuelPriceService;
import com.evmonitor.infrastructure.persistence.JpaEvLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingPriceServiceTest {

    @Mock
    private JpaEvLogRepository evLogRepository;

    @Mock
    private FuelPriceService fuelPriceService;

    @Mock
    private ChargingProviderTariffRepository tariffRepository;

    @InjectMocks
    private ChargingPriceService service;

    @Test
    void usesCommunityAveragesWhenSampleIsLargeEnough() {
        // [home_price, public_price, home_count, public_count]
        when(evLogRepository.findCommunityChargingPrices(false))
                .thenReturn(new Object[]{0.284, 0.561, 1200L, 800L});

        ChargingReferencePrices prices = service.getReferencePrices(false);

        assertThat(prices.homePricePerKwh()).isEqualByComparingTo("0.2840");
        assertThat(prices.publicPricePerKwh()).isEqualByComparingTo("0.5610");
    }

    @Test
    void fallsBackToDefaultsWhenBucketTooThin() {
        when(evLogRepository.findCommunityChargingPrices(false))
                .thenReturn(new Object[]{0.10, 0.90, 5L, 3L}); // below MIN_SESSIONS

        ChargingReferencePrices prices = service.getReferencePrices(false);

        assertThat(prices.homePricePerKwh()).isEqualByComparingTo(ChargingPriceService.DEFAULT_HOME_PRICE);
        assertThat(prices.publicPricePerKwh()).isEqualByComparingTo(ChargingPriceService.DEFAULT_PUBLIC_PRICE);
    }

    @Test
    void fallsBackWhenValuesAreNull() {
        when(evLogRepository.findCommunityChargingPrices(false))
                .thenReturn(new Object[]{null, null, 0L, 0L});

        ChargingReferencePrices prices = service.getReferencePrices(false);

        assertThat(prices.homePricePerKwh()).isEqualByComparingTo(ChargingPriceService.DEFAULT_HOME_PRICE);
        assertThat(prices.publicPricePerKwh()).isEqualByComparingTo(ChargingPriceService.DEFAULT_PUBLIC_PRICE);
    }

    @Test
    void mixedBuckets_oneTrusted_oneFallback() {
        when(evLogRepository.findCommunityChargingPrices(false))
                .thenReturn(new Object[]{0.31, 0.70, 500L, 4L}); // home trusted, public too thin

        ChargingReferencePrices prices = service.getReferencePrices(false);

        assertThat(prices.homePricePerKwh()).isEqualByComparingTo("0.3100");
        assertThat(prices.publicPricePerKwh()).isEqualByComparingTo(ChargingPriceService.DEFAULT_PUBLIC_PRICE);
    }

    @Test
    void includesFuelPricesAndTheCombustionConstant() {
        when(evLogRepository.findCommunityChargingPrices(false))
                .thenReturn(new Object[]{0.30, 0.55, 500L, 400L});
        when(fuelPriceService.getBenzinPrice()).thenReturn(1.789);
        when(fuelPriceService.getDieselPrice()).thenReturn(1.659);

        ChargingReferencePrices prices = service.getReferencePrices(false);

        assertThat(prices.petrolPricePerLiter()).isEqualByComparingTo("1.789");
        assertThat(prices.dieselPricePerLiter()).isEqualByComparingTo("1.659");
        assertThat(prices.combustionLitersPer100km()).isEqualByComparingTo(FuelPriceService.COMBUSTION_LITERS_PER_100_KM);
    }

    @Test
    void fastAdHocPrice_isTheMeanOfTheBigNetworksWithoutSubscription() {
        when(evLogRepository.findCommunityChargingPrices(false)).thenReturn(new Object[]{0.28, 0.45, 1200L, 800L});
        // per network the dearest DC tariff without monthly fee: what someone pays without a contract
        when(tariffRepository.findCurrentTariffsByEmp("EnBW")).thenReturn(List.of(
                tariff("EnBW", ChargingType.DC, "0.56", "0"), tariff("EnBW", ChargingType.DC, "0.39", "11.99"),
                tariff("EnBW", ChargingType.AC, "0.59", "0")));
        when(tariffRepository.findCurrentTariffsByEmp("IONITY")).thenReturn(List.of(
                tariff("IONITY", ChargingType.DC, "0.69", "0"), tariff("IONITY", ChargingType.DC, "0.39", "0")));
        when(tariffRepository.findCurrentTariffsByEmp("Fastned")).thenReturn(List.of(
                tariff("Fastned", ChargingType.DC, "0.69", "0"), tariff("Fastned", ChargingType.DC, "0.48", "11.99")));

        // (0.56 + 0.69 + 0.69) / 3
        assertThat(service.getReferencePrices(false).fastAdHocPricePerKwh()).isEqualByComparingTo("0.6467");
    }

    @Test
    void fastAdHocPrice_isNullWithoutAnyTariff() {
        when(evLogRepository.findCommunityChargingPrices(false)).thenReturn(new Object[]{0.28, 0.45, 1200L, 800L});
        when(tariffRepository.findCurrentTariffsByEmp(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());

        assertThat(service.getReferencePrices(false).fastAdHocPricePerKwh()).isNull();
    }

    private static ChargingProviderTariff tariff(String emp, ChargingType type, String price, String monthlyFee) {
        return new ChargingProviderTariff(java.util.UUID.randomUUID(), emp, "v", null, null, type, new BigDecimal(price),
                BigDecimal.ZERO, new BigDecimal(monthlyFee), null, null, false, java.time.LocalDate.of(2025, 1, 1), null, null, null);
    }
}
