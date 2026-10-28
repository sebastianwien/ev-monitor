package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.ChargingBenchmarkCache;
import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository.PublicChargePrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drivers as a sensor: where at least three drivers paid with a card at one operator (five
 * charges), their median is compared with the nearest maintained variant of that card. More
 * than 10 % apart is a hint for the admin, never an automatic change.
 */
class DriverPriceCheckTest {

    private final ChargingBenchmarkCache cache = mock(ChargingBenchmarkCache.class);
    private final ChargingProviderTariffRepository tariffs = mock(ChargingProviderTariffRepository.class);
    private final List<PublicChargePrice> rows = new ArrayList<>();
    private final List<ChargingProviderTariff> valid = new ArrayList<>();
    private DriverPriceCheck check;

    @BeforeEach
    void setUp() {
        when(tariffs.findAllKnownCpoNames()).thenReturn(List.of("EnBW", "IONITY"));
        CpoNameNormalizer normalizer = new CpoNameNormalizer(tariffs);
        normalizer.init();
        when(cache.publicChargePrices()).thenReturn(rows);
        when(tariffs.findValidOn(any())).thenReturn(valid);
        lenient().when(tariffs.findTierForCpo(any(), any())).thenReturn(Optional.empty());
        check = new DriverPriceCheck(cache, tariffs, normalizer);
    }

    @Test
    void driversPayingClearlyMore_isAHint_withTheNearestVariant() {
        valid.add(tariff("IONITY", "Power", "0.42"));
        valid.add(tariff("IONITY", "Direct", "0.72"));
        drivers("IONITY", "IONITY", "0.55", "0.55", "0.56", "0.54", "0.55");

        List<DriverPriceCheck.Deviation> hints = check.deviations();

        assertThat(hints).singleElement().satisfies(d -> {
            assertThat(d.empName()).isEqualTo("IONITY");
            assertThat(d.operator()).isEqualTo("IONITY");
            assertThat(d.nearestVariant()).isEqualTo("Power");
            assertThat(d.curatedPricePerKwh()).isEqualByComparingTo("0.42");
            assertThat(d.driverMedianPricePerKwh()).isEqualByComparingTo("0.55");
            assertThat(d.drivers()).isEqualTo(5);
        });
    }

    @Test
    void medianNearAnyVariant_isNoHint() {
        valid.add(tariff("EnBW", "Tariff L", "0.39"));
        valid.add(tariff("EnBW", "Tariff S", "0.56"));
        drivers("EnBW", "EnBW", "0.56", "0.55", "0.57", "0.56", "0.56");

        assertThat(check.deviations()).isEmpty();
    }

    @Test
    void tooFewDrivers_isNoHint() {
        valid.add(tariff("IONITY", "Power", "0.42"));
        drivers("IONITY", "IONITY", "0.69", "0.69");

        assertThat(check.deviations()).isEmpty();
    }

    @Test
    void cardWithoutMaintainedTariffThere_isNoHint() {
        drivers("EnBW", "IONITY", "0.69", "0.69", "0.69", "0.69", "0.69");

        assertThat(check.deviations()).isEmpty();
    }

    private void drivers(String cardEmp, String operator, String... prices) {
        for (String p : prices) {
            rows.add(new PublicChargePrice(UUID.randomUUID(), operator, ChargingType.DC, new BigDecimal(p), cardEmp + " Karte", cardEmp));
        }
    }

    private static ChargingProviderTariff tariff(String emp, String variant, String price) {
        return ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp).tariffVariant(variant)
                .chargingType(ChargingType.DC).pricePerKwh(new BigDecimal(price)).sessionFeeEur(BigDecimal.ZERO)
                .monthlyFeeEur(BigDecimal.ZERO).validFrom(LocalDate.of(2026, 1, 1)).build();
    }
}
