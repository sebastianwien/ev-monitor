package com.evmonitor.application;

import com.evmonitor.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StripeServicePriceResolutionTest {

    private StripeService stripeService;

    @BeforeEach
    void setUp() {
        stripeService = new StripeService(mock(UserRepository.class));
        ReflectionTestUtils.setField(stripeService, "priceIdMonthly",     "price_monthly_eur_de");
        ReflectionTestUtils.setField(stripeService, "priceIdYearly",      "price_yearly_eur_de");
        ReflectionTestUtils.setField(stripeService, "priceIdMonthlyIntl", "price_monthly_eur_intl");
        ReflectionTestUtils.setField(stripeService, "priceIdYearlyIntl",  "price_yearly_eur_intl");
        ReflectionTestUtils.setField(stripeService, "priceIdMonthlyUsd",  "price_monthly_usd");
        ReflectionTestUtils.setField(stripeService, "priceIdYearlyUsd",   "price_yearly_usd");
        ReflectionTestUtils.setField(stripeService, "priceIdMonthlyGbp",  "price_monthly_gbp");
        ReflectionTestUtils.setField(stripeService, "priceIdYearlyGbp",   "price_yearly_gbp");
        ReflectionTestUtils.setField(stripeService, "priceIdMonthlyNok",  "price_monthly_nok");
        ReflectionTestUtils.setField(stripeService, "priceIdYearlyNok",   "price_yearly_nok");
        ReflectionTestUtils.setField(stripeService, "priceIdMonthlySek",  "price_monthly_sek");
        ReflectionTestUtils.setField(stripeService, "priceIdYearlySek",   "price_yearly_sek");
        ReflectionTestUtils.setField(stripeService, "priceIdMonthlyDkk",  "price_monthly_dkk");
        ReflectionTestUtils.setField(stripeService, "priceIdYearlyDkk",   "price_yearly_dkk");
    }

    @Test
    void de_gets_base_eur_price() {
        assertThat(stripeService.resolvePriceId("monthly", "DE")).isEqualTo("price_monthly_eur_de");
        assertThat(stripeService.resolvePriceId("yearly",  "DE")).isEqualTo("price_yearly_eur_de");
    }

    @Test
    void at_and_ch_get_base_eur_price() {
        assertThat(stripeService.resolvePriceId("monthly", "AT")).isEqualTo("price_monthly_eur_de");
        assertThat(stripeService.resolvePriceId("monthly", "CH")).isEqualTo("price_monthly_eur_de");
        assertThat(stripeService.resolvePriceId("yearly",  "AT")).isEqualTo("price_yearly_eur_de");
        assertThat(stripeService.resolvePriceId("yearly",  "CH")).isEqualTo("price_yearly_eur_de");
    }

    @Test
    void nl_and_be_get_intl_eur_price() {
        assertThat(stripeService.resolvePriceId("monthly", "NL")).isEqualTo("price_monthly_eur_intl");
        assertThat(stripeService.resolvePriceId("monthly", "BE")).isEqualTo("price_monthly_eur_intl");
        assertThat(stripeService.resolvePriceId("yearly",  "NL")).isEqualTo("price_yearly_eur_intl");
        assertThat(stripeService.resolvePriceId("yearly",  "BE")).isEqualTo("price_yearly_eur_intl");
    }

    @Test
    void us_gets_usd_price() {
        assertThat(stripeService.resolvePriceId("monthly", "US")).isEqualTo("price_monthly_usd");
        assertThat(stripeService.resolvePriceId("yearly",  "US")).isEqualTo("price_yearly_usd");
    }

    @Test
    void gb_gets_gbp_price() {
        assertThat(stripeService.resolvePriceId("monthly", "GB")).isEqualTo("price_monthly_gbp");
        assertThat(stripeService.resolvePriceId("yearly",  "GB")).isEqualTo("price_yearly_gbp");
    }

    @Test
    void no_gets_nok_price() {
        assertThat(stripeService.resolvePriceId("monthly", "NO")).isEqualTo("price_monthly_nok");
        assertThat(stripeService.resolvePriceId("yearly",  "NO")).isEqualTo("price_yearly_nok");
    }

    @Test
    void se_gets_sek_price() {
        assertThat(stripeService.resolvePriceId("monthly", "SE")).isEqualTo("price_monthly_sek");
        assertThat(stripeService.resolvePriceId("yearly",  "SE")).isEqualTo("price_yearly_sek");
    }

    @Test
    void dk_gets_dkk_price() {
        assertThat(stripeService.resolvePriceId("monthly", "DK")).isEqualTo("price_monthly_dkk");
        assertThat(stripeService.resolvePriceId("yearly",  "DK")).isEqualTo("price_yearly_dkk");
    }

    @Test
    void unknown_country_gets_intl_eur_price() {
        assertThat(stripeService.resolvePriceId("monthly", "FR")).isEqualTo("price_monthly_eur_intl");
        assertThat(stripeService.resolvePriceId("monthly", "JP")).isEqualTo("price_monthly_eur_intl");
        assertThat(stripeService.resolvePriceId("yearly",  "FR")).isEqualTo("price_yearly_eur_intl");
    }

    @Test
    void null_country_defaults_to_de_price() {
        assertThat(stripeService.resolvePriceId("monthly", null)).isEqualTo("price_monthly_eur_de");
        assertThat(stripeService.resolvePriceId("yearly",  null)).isEqualTo("price_yearly_eur_de");
    }
}
