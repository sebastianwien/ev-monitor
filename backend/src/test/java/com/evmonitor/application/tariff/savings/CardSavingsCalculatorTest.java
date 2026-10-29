package com.evmonitor.application.tariff.savings;

import com.evmonitor.application.tariff.TariffApplicability;
import com.evmonitor.application.tariff.savings.CardSavingsCalculator.Charge;
import com.evmonitor.application.tariff.savings.CardSavingsCalculator.Offer;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a card would have saved on one charge. Card cost = kWh at the charger times the card
 * price, plus start fee and blocking fee. A monthly fee is shared over the kWh the card would
 * have saved money on in the 30 days up to this charge; a card only shows if it saves at least
 * one euro on this charge and over the month.
 */
class CardSavingsCalculatorTest {

    private static final TariffApplicability.TierLookup NO_TIERS = (emp, op) -> Optional.empty();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 26);

    @Test
    void cardWithoutFee_savesTheDifferenceTimesKwh() {
        Charge ionity = charge("IONITY", "40", "35.60", 30);
        var maingau = card("Maingau", "Standard").price("0.62").elsewhere().build();

        List<Offer> offers = CardSavingsCalculator.offers(ionity, List.of(ionity), List.of(maingau), NO_TIERS);

        assertThat(offers).singleElement().satisfies(o -> {
            assertThat(o.chargeSavingsEur()).isEqualByComparingTo("10.80");
            assertThat(o.feeShareEur()).isEqualByComparingTo("0");
            assertThat(o.pricePerKwh()).isEqualByComparingTo("0.62");
        });
    }

    @Test
    void monthlyFee_isSharedOverTheKwhTheCardWouldHaveSavedOn() {
        Charge today = charge("IONITY", "40", "35.60", 30);
        Charge earlier = charge("IONITY", "60", "53.40", 30);
        var power = card("IONITY", "Power").price("0.42").fee("11.99").build();

        Offer o = CardSavingsCalculator.offers(today, List.of(today, earlier), List.of(power), NO_TIERS).get(0);

        // 40 von 100 kWh: 40 % der 11,99 € Grundgebühr = 4,80 €; 35,60 - 16,80 - 4,80 = 14,00 €
        assertThat(o.feeShareEur()).isEqualByComparingTo("4.80");
        assertThat(o.chargeSavingsEur()).isEqualByComparingTo("14.00");
        // (35,60 - 16,80) + (53,40 - 25,20) - 11,99
        assertThat(o.monthSavingsEur()).isEqualByComparingTo("35.01");
    }

    @Test
    void feeThatDoesNotPayOff_hidesTheCard() {
        Charge small = charge("IONITY", "10", "5.50", 15);
        var power = card("IONITY", "Power").price("0.42").fee("11.99").build();

        assertThat(CardSavingsCalculator.offers(small, List.of(small), List.of(power), NO_TIERS)).isEmpty();
    }

    @Test
    void lessThanOneEuro_orDearer_isNoOffer() {
        Charge c = charge("IONITY", "10", "6.80", 15);
        var almost = card("A", "x").price("0.61").elsewhere().build();
        var dearer = card("B", "x").price("0.75").elsewhere().build();

        assertThat(CardSavingsCalculator.offers(c, List.of(c), List.of(almost, dearer), NO_TIERS)).isEmpty();
    }

    @Test
    void startFee_andBlockingFee_count_withTheCap() {
        Charge long_ = charge("IONITY", "40", "50.00", 300);
        var blocking = card("Maingau", "Standard").price("0.62").elsewhere().blocking("0.10", 60, "12").build();
        var start = card("C", "x").price("0.55").elsewhere().session("1.00").build();

        List<Offer> offers = CardSavingsCalculator.offers(long_, List.of(long_), List.of(blocking, start), NO_TIERS);

        assertThat(offers).extracting(Offer::name).containsExactly("C", "Maingau");
        // C: 50,00 - (40 x 0,55 + 1,00) = 27,00
        assertThat(offers.get(0).chargeSavingsEur()).isEqualByComparingTo("27.00");
        // Maingau: 240 Minuten über der Schwelle wären 24 €, gedeckelt auf 12 €: 50,00 - (24,80 + 12,00) = 13,20
        assertThat(offers.get(1).chargeSavingsEur()).isEqualByComparingTo("13.20");
    }

    @Test
    void blockingFeeWithUnknownDuration_hidesTheCard() {
        Charge noDuration = charge("IONITY", "40", "35.60", null);
        var blocking = card("Maingau", "Standard").price("0.62").elsewhere().blocking("0.10", 60, null).build();

        assertThat(CardSavingsCalculator.offers(noDuration, List.of(noDuration), List.of(blocking), NO_TIERS)).isEmpty();
    }

    @Test
    void cardNotValidAtThisOperator_isNoOffer() {
        Charge c = charge("IONITY", "40", "35.60", 30);
        var enbw = card("EnBW", "Tariff L").price("0.39").fee("11.99").build();

        assertThat(CardSavingsCalculator.offers(c, List.of(c), List.of(enbw), NO_TIERS)).isEmpty();
    }

    @Test
    void offers_sortedBySavingsOnThisCharge() {
        Charge c = charge("IONITY", "40", "35.60", 30);
        var a = card("A", "x").price("0.70").elsewhere().build();
        var b = card("B", "x").price("0.50").elsewhere().build();

        assertThat(CardSavingsCalculator.offers(c, List.of(c), List.of(a, b), NO_TIERS)).extracting(Offer::name).containsExactly("B", "A");
    }

    @Test
    void onlyTheBestVariantPerProvider_atMostThreeCards() {
        Charge c = charge("IONITY", "40", "35.60", 30);
        var go = card("IONITY", "Go").price("0.68").build();
        var direct = card("IONITY", "Direct").price("0.72").build();
        var a = card("A", "x").price("0.50").elsewhere().build();
        var b = card("B", "x").price("0.55").elsewhere().build();
        var d = card("D", "x").price("0.60").elsewhere().build();

        List<Offer> offers = CardSavingsCalculator.offers(c, List.of(c), List.of(go, direct, a, b, d), NO_TIERS);

        assertThat(offers).extracting(Offer::name).containsExactly("A", "B", "D");
        assertThat(CardSavingsCalculator.offers(c, List.of(c), List.of(go, direct), NO_TIERS))
                .extracting(Offer::variant).containsExactly("Go");
    }

    private static Charge charge(String operator, String kwh, String cost, Integer minutes) {
        return new Charge(UUID.randomUUID(), operator, ChargingType.DC, 150, new BigDecimal(kwh), new BigDecimal(cost), minutes, DAY);
    }

    private static Card card(String emp, String variant) {
        return new Card(ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp).tariffVariant(variant)
                .chargingType(ChargingType.DC).sessionFeeEur(BigDecimal.ZERO).monthlyFeeEur(BigDecimal.ZERO)
                .validFrom(LocalDate.of(2026, 1, 1)).lastVerifiedAt(DAY.atStartOfDay()));
    }

    private record Card(ChargingProviderTariff.ChargingProviderTariffBuilder b) {
        Card price(String p) { b.pricePerKwh(new BigDecimal(p)); return this; }
        Card fee(String f) { b.monthlyFeeEur(new BigDecimal(f)); return this; }
        Card session(String f) { b.sessionFeeEur(new BigDecimal(f)); return this; }
        Card elsewhere() { b.appliesElsewhere(true); return this; }
        Card blocking(String perMin, int after, String cap) {
            b.blockingFeePerMin(new BigDecimal(perMin)).blockingFeeAfterMin(after).blockingFeeCapEur(cap == null ? null : new BigDecimal(cap));
            return this;
        }
        ChargingProviderTariff build() { return b.build(); }
    }
}
