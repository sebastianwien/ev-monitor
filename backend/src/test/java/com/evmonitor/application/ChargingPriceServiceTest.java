package com.evmonitor.application;

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
}
