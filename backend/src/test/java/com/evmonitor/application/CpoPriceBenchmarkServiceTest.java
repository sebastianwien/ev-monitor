package com.evmonitor.application;

import com.evmonitor.application.CpoPriceBenchmark.CardOffer;
import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository.PublicChargePrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * What other drivers pay at one operator, and which cards are proven to be cheaper there.
 * Only aggregates from five other drivers on (per card three), each driver counts once, the
 * asking driver never counts. A card only counts where it is proven: paid there by drivers, or
 * a maintained tariff of the operator's own network or mapped to it by tier.
 */
@ExtendWith(MockitoExtension.class)
class CpoPriceBenchmarkServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final LocalDateTime VERIFIED = LocalDateTime.of(2026, 3, 25, 10, 0);

    @Mock ChargingBenchmarkCache benchmarkCache;
    @Mock ChargingProviderTariffRepository tariffRepository;

    private CpoPriceBenchmarkService service;
    private final List<PublicChargePrice> rows = new ArrayList<>();
    private final List<ChargingProviderTariff> tariffs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(tariffRepository.findAllKnownCpoNames()).thenReturn(List.of("ALDI Süd", "EnBW", "IONITY", "Tesla Supercharger"));
        CpoNameNormalizer normalizer = new CpoNameNormalizer(tariffRepository);
        normalizer.init();
        lenient().when(benchmarkCache.publicChargePrices()).thenReturn(rows);
        lenient().when(tariffRepository.findValidOn(any())).thenReturn(tariffs);
        service = new CpoPriceBenchmarkService(benchmarkCache, tariffRepository, normalizer);
    }

    @Test
    void fiveOtherDrivers_giveTheMedianOfTheirOwnMedians() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59");
        // a frequent charger counts once, not ten times
        UUID frequent = UUID.randomUUID();
        IntStream.range(0, 10).forEach(i -> rows.add(price(frequent, "EnBW", ChargingType.DC, "0.69")));

        CpoPriceBenchmark b = service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow();

        assertThat(b.cpoName()).isEqualTo("EnBW");
        assertThat(b.drivers()).isEqualTo(5);
        assertThat(b.medianPricePerKwh()).isEqualByComparingTo("0.49");
        assertThat(b.lowerQuartilePricePerKwh()).isEqualByComparingTo("0.43");
        assertThat(b.upperQuartilePricePerKwh()).isEqualByComparingTo("0.59");
    }

    @Test
    void fourOtherDrivers_noBenchmark() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59");

        assertThat(service.benchmark("EnBW", ChargingType.DC, ME)).isEmpty();
    }

    @Test
    void theAskingDriverNeverCounts() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59");
        rows.add(price(ME, "EnBW", ChargingType.DC, "0.99"));
        assertThat(service.benchmark("EnBW", ChargingType.DC, ME)).as("four others are not enough").isEmpty();

        drivers("EnBW", ChargingType.DC, "0.69");
        CpoPriceBenchmark b = service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow();
        assertThat(b.drivers()).isEqualTo(5);
        assertThat(b.medianPricePerKwh()).isEqualByComparingTo("0.49");
    }

    @Test
    void teslaSiteNames_countAsTheSupercharger() {
        rows.add(price(UUID.randomUUID(), "Tesla Supercharger Graz, Austria", ChargingType.DC, "0.36"));
        rows.add(price(UUID.randomUUID(), "Tesla Supercharger Nossen, Germany", ChargingType.DC, "0.39"));
        drivers("Tesla Supercharger", ChargingType.DC, "0.39", "0.42", "0.45");

        CpoPriceBenchmark b = service.benchmark("Tesla Supercharger Herrieden, Germany", ChargingType.DC, ME).orElseThrow();

        assertThat(b.cpoName()).isEqualTo("Tesla Supercharger");
        assertThat(b.drivers()).isEqualTo(5);
    }

    @Test
    void onlyCatalogOperators_matchedRegardlessOfCase() {
        drivers("Zuhause", ChargingType.DC, "0.30", "0.30", "0.30", "0.30", "0.30");
        drivers("enbw", ChargingType.DC, "0.39", "0.43", "0.49", "0.59", "0.69");

        assertThat(service.benchmark("Zuhause", ChargingType.DC, ME)).isEmpty();
        assertThat(service.benchmark("ENBW", ChargingType.DC, ME)).hasValueSatisfying(b -> assertThat(b.cpoName()).isEqualTo("EnBW"));
    }

    @Test
    void implausiblePrices_areLeftOut() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59", "0.69");
        // typos: 2.06 instead of 0.206, 0.05 for a near-free session
        drivers("EnBW", ChargingType.DC, "2.06", "0.05");

        assertThat(service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow().drivers()).isEqualTo(5);
    }

    @Test
    void acAndDc_areSeparate() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59", "0.69");

        assertThat(service.benchmark("EnBW", ChargingType.AC, ME)).isEmpty();
    }

    @Test
    void catalogCardPaidThereByThreeOtherDrivers_isOffered_underItsCatalogName() {
        drivers("EnBW", ChargingType.DC, "0.59", "0.69");
        // spelled differently, same catalog provider
        rows.add(card(UUID.randomUUID(), "Maingau Energie", "Maingau", "0.39"));
        rows.add(card(UUID.randomUUID(), "maingau ", "Maingau", "0.39"));
        rows.add(card(UUID.randomUUID(), "Maingau EinStrom", "Maingau", "0.41"));
        // two drivers are not enough for a card, and my own card does not count
        rows.add(card(UUID.randomUUID(), "Plugsurfing", "Plugsurfing", "0.45"));
        rows.add(card(UUID.randomUUID(), "Plugsurfing", "Plugsurfing", "0.45"));
        rows.add(card(ME, "Plugsurfing", "Plugsurfing", "0.45"));

        List<CardOffer> cards = service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow().cards();

        assertThat(cards).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("Maingau");
            assertThat(c.pricePerKwh()).isEqualByComparingTo("0.39");
            assertThat(c.source()).isEqualTo(CardOffer.Source.DRIVERS);
            assertThat(c.drivers()).isEqualTo(3);
            assertThat(c.monthlyFeeEur()).isNull();
        });
    }

    @Test
    void freeTextCardWithoutCatalogProvider_isNeverOffered() {
        // three made-up accounts naming a card of their choosing must not advertise it to everyone
        drivers("EnBW", ChargingType.DC, "0.59", "0.69");
        IntStream.range(0, 3).forEach(i -> rows.add(card(UUID.randomUUID(), "MeineKarte", null, "0.15")));

        assertThat(service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow().cards()).isEmpty();
    }

    @Test
    void maintainedTariff_countsInItsOwnNetworkOnly() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59", "0.69");
        tariffs.add(tariff("EnBW", "Tariff L", null, null, "0.39", "11.99"));
        tariffs.add(tariff("IONITY", "Power", null, null, "0.39", "11.99"));

        List<CardOffer> cards = service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow().cards();

        assertThat(cards).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("EnBW");
            assertThat(c.variant()).isEqualTo("Tariff L");
            assertThat(c.monthlyFeeEur()).isEqualByComparingTo("11.99");
            assertThat(c.source()).isEqualTo(CardOffer.Source.CURATED);
            assertThat(c.verifiedOn()).isEqualTo(VERIFIED.toLocalDate());
            assertThat(c.drivers()).isNull();
        });
    }

    @Test
    void maintainedTariff_countsWhereItsTierIsMappedOrItNamesTheOperator() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59", "0.69");
        tariffs.add(tariff("Maingau", "Standard", null, "LOW", "0.62", "0"));
        tariffs.add(tariff("Maingau", "Standard", null, "HIGH", "0.82", "0"));
        tariffs.add(tariff("ADAC e-Charge", "Standard", "EnBW", null, "0.55", "0"));
        tariffs.add(tariff("ADAC e-Charge", "Standard", "IONITY", null, "0.50", "0"));
        when(tariffRepository.findTierForCpo("Maingau", "EnBW")).thenReturn(Optional.of("HIGH"));

        List<CardOffer> cards = service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow().cards();

        assertThat(cards).extracting(CardOffer::name, CardOffer::pricePerKwh)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ADAC e-Charge", new BigDecimal("0.550")),
                        org.assertj.core.groups.Tuple.tuple("Maingau", new BigDecimal("0.820")));
    }

    @Test
    void unmappedTier_doesNotCount() {
        drivers("ALDI Süd", ChargingType.DC, "0.47", "0.47", "0.47", "0.47", "0.47");
        tariffs.add(tariff("Maingau", "Standard", null, "STANDARD", "0.72", "0"));
        when(tariffRepository.findTierForCpo("Maingau", "ALDI Süd")).thenReturn(Optional.empty());

        assertThat(service.benchmark("ALDI Süd", ChargingType.DC, ME).orElseThrow().cards()).isEmpty();
    }

    @Test
    void elsewhereTariff_countsAtEveryOperatorWithoutAMoreSpecificPrice() {
        drivers("ALDI Süd", ChargingType.DC, "0.47", "0.47", "0.47", "0.47", "0.47");
        tariffs.add(tariff("ADAC e-Charge", "Standard", "EnBW", null, "0.55", "0"));
        tariffs.add(tariff("ADAC e-Charge", "Standard", null, null, "0.75", "0").toBuilder().appliesElsewhere(true).build());

        assertThat(service.benchmark("ALDI Süd", ChargingType.DC, ME).orElseThrow().cards())
                .extracting(CardOffer::pricePerKwh).containsExactly(new BigDecimal("0.750"));
    }

    @Test
    void dynamicTariff_isNeverOffered() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59", "0.69");
        tariffs.add(tariff("EnBW", "Tariff L", null, null, "0.39", "11.99").toBuilder().dynamicPricing(true).build());

        assertThat(service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow().cards()).isEmpty();
    }

    @Test
    void maintainedTariff_replacesTheDriverCardOfTheSameProvider() {
        drivers("EnBW", ChargingType.DC, "0.59", "0.69");
        IntStream.range(0, 3).forEach(i -> rows.add(card(UUID.randomUUID(), "EnBW", "EnBW", "0.39")));
        tariffs.add(tariff("EnBW", "Tariff L", null, null, "0.39", "11.99"));

        assertThat(service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow().cards())
                .extracting(CardOffer::source).containsExactly(CardOffer.Source.CURATED);
    }

    @Test
    void cards_cheapestFirst_atMostFive() {
        drivers("EnBW", ChargingType.DC, "0.39", "0.43", "0.49", "0.59", "0.69");
        tariffs.add(tariff("EnBW", "Tariff S", null, null, "0.56", "0"));
        tariffs.add(tariff("EnBW", "Tariff M", null, null, "0.46", "5.99"));
        tariffs.add(tariff("EnBW", "Tariff L", null, null, "0.39", "11.99"));
        tariffs.add(tariff("ADAC e-Charge", "A", "EnBW", null, "0.55", "0"));
        tariffs.add(tariff("ADAC e-Charge", "B", "EnBW", null, "0.57", "0"));
        tariffs.add(tariff("ADAC e-Charge", "C", "EnBW", null, "0.58", "0"));

        assertThat(service.benchmark("EnBW", ChargingType.DC, ME).orElseThrow().cards())
                .extracting(CardOffer::variant)
                .containsExactly("Tariff L", "Tariff M", "A", "Tariff S", "B");
    }

    /** One driver per price, each with one session. */
    private void drivers(String cpo, ChargingType type, String... prices) {
        for (String p : prices) rows.add(price(UUID.randomUUID(), cpo, type, p));
    }

    private static PublicChargePrice price(UUID user, String cpo, ChargingType type, String price) {
        return new PublicChargePrice(user, cpo, type, new BigDecimal(price), null, null);
    }

    private static PublicChargePrice card(UUID user, String cardName, String cardEmp, String price) {
        return new PublicChargePrice(user, "EnBW", ChargingType.DC, new BigDecimal(price), cardName, cardEmp);
    }

    private static ChargingProviderTariff tariff(String emp, String variant, String cpo, String tier, String price, String fee) {
        return ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp).tariffVariant(variant)
                .cpoName(cpo).priceTier(tier).chargingType(ChargingType.DC).pricePerKwh(new BigDecimal(price))
                .sessionFeeEur(BigDecimal.ZERO).monthlyFeeEur(new BigDecimal(fee))
                .validFrom(LocalDate.of(2025, 1, 1)).lastVerifiedAt(VERIFIED).build();
    }
}
