package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.tariff.crawl.ExtractedTariff.Scope;
import com.evmonitor.application.tariff.crawl.TariffReconciler.Result;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Page against table. A known tariff is matched by variant, charging type, scope and power
 * class; a value only counts as changed if the page states it. Anything doubtful (several
 * prices for one tariff, a jump of more than 40 %, implausible amounts) is held for the admin.
 * A tariff counts as missing only if the page was readable for this provider at all.
 */
class TariffReconcilerTest {

    @Test
    void sameValues_areUnchanged() {
        var l = known("Tariff L", null, "0.39", "11.99");

        Result r = TariffReconciler.reconcile(List.of(l), List.of(found("Tariff L", "0.39", "11.99")));

        assertThat(r.unchanged()).containsExactly(l);
        assertThat(r.changes()).isEmpty();
    }

    @Test
    void otherPrice_isAChange_withoutHold() {
        var l = known("Tariff L", null, "0.39", "11.99");

        Result r = TariffReconciler.reconcile(List.of(l), List.of(found("Tariff L", "0.42", "11.99")));

        assertThat(r.changes()).singleElement().satisfies(c -> {
            assertThat(c.current()).isEqualTo(l);
            assertThat(c.found().pricePerKwh()).isEqualByComparingTo("0.42");
            assertThat(c.holdReason()).isNull();
        });
    }

    @Test
    void variantSpelling_doesNotMatter() {
        var l = known("Tariff L", null, "0.39", "11.99");

        assertThat(TariffReconciler.reconcile(List.of(l), List.of(found("Ladetarif L", "0.39", "11.99"))).unchanged()).containsExactly(l);
    }

    @Test
    void feeNotStatedOnThePage_isNoChange() {
        var l = known("Tariff L", null, "0.39", "11.99");

        assertThat(TariffReconciler.reconcile(List.of(l), List.of(found("Tariff L", "0.39", null))).unchanged()).containsExactly(l);
    }

    @Test
    void severalPricesForOneTariff_areHeld() {
        var power = known("Power", null, "0.39", "11.99");

        Result r = TariffReconciler.reconcile(List.of(power), List.of(found("Power", "0.39", "11.99"), found("Power", "0.42", "11.99")));

        assertThat(r.unchanged()).isEmpty();
        assertThat(r.changes()).singleElement().satisfies(c -> assertThat(c.holdReason()).contains("0,39").contains("0,42"));
    }

    @Test
    void bigJump_orImplausibleAmount_isHeld() {
        var l = known("Tariff L", null, "0.39", "11.99");

        assertThat(TariffReconciler.reconcile(List.of(l), List.of(found("Tariff L", "0.69", "11.99"))).changes().get(0).holdReason())
                .contains("Sprung");
        assertThat(TariffReconciler.reconcile(List.of(l), List.of(found("Tariff L", "0.39", "49.00"))).changes().get(0).holdReason())
                .contains("Grundgebühr");
    }

    @Test
    void tariffOnlyOnThePage_isNew() {
        var l = known("Tariff L", null, "0.39", "11.99");

        Result r = TariffReconciler.reconcile(List.of(l), List.of(found("Tariff L", "0.39", "11.99"), found("Tariff XL", "0.35", "19.99")));

        assertThat(r.added()).extracting(ExtractedTariff::variant).containsExactly("Tariff XL");
    }

    @Test
    void tariffOnlyInTheTable_isMissing_whenThePageWasReadable() {
        var l = known("Tariff L", null, "0.39", "11.99");
        var s = known("Tariff S", null, "0.56", "0");

        assertThat(TariffReconciler.reconcile(List.of(l, s), List.of(found("Tariff L", "0.39", "11.99"))).missing()).containsExactly(s);
        assertThat(TariffReconciler.reconcile(List.of(l, s), List.of(found("Ganz anders", "0.50", "0"))).missing())
                .as("nothing matched: page probably broken, nothing counts as missing").isEmpty();
    }

    @Test
    void scopeAndPowerClass_separateTariffs() {
        var low = known("Standard", "LOW", "0.62", "0");
        var high = known("Standard", "HIGH", "0.82", "0");
        var hpc = known("Klassik", null, "0.62", "0").toBuilder().minPowerKw(50).build();
        var slow = known("Klassik", null, "0.52", "0");

        Result r = TariffReconciler.reconcile(List.of(low, high, hpc, slow), List.of(
                tier("Standard", "LOW", "0.62"), tier("Standard", "HIGH", "0.82"),
                foundAbove("Klassik", "0.62", 50), found("Klassik", "0.52", "0")));

        assertThat(r.unchanged()).containsExactlyInAnyOrder(low, high, hpc, slow);
    }

    @Test
    void zeroOnThePage_andNothingInTheTable_isNoChange() {
        var l = ChargingProviderTariff.builder().id(UUID.randomUUID()).empName("EnBW").tariffVariant("Tariff L")
                .chargingType(ChargingType.DC).pricePerKwh(new BigDecimal("0.39")).validFrom(LocalDate.of(2025, 12, 1)).build();
        var page = new ExtractedTariff("Tariff L", "DC", new BigDecimal("0.39"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, Scope.OWN_NETWORK, null, null, null, null, false, "Zitat 0.39");

        Result r = TariffReconciler.reconcile(List.of(l), List.of(page));

        assertThat(r.changes()).as("no fee and no blocking fee are the same as 0").isEmpty();
        assertThat(r.unchanged()).containsExactly(l);
    }

    private static ChargingProviderTariff known(String variant, String tier, String price, String fee) {
        return ChargingProviderTariff.builder().id(UUID.randomUUID()).empName("EnBW").tariffVariant(variant).priceTier(tier)
                .chargingType(ChargingType.DC).pricePerKwh(new BigDecimal(price)).sessionFeeEur(BigDecimal.ZERO)
                .monthlyFeeEur(new BigDecimal(fee)).validFrom(LocalDate.of(2025, 12, 1)).build();
    }

    private static ExtractedTariff found(String variant, String price, String fee) {
        return extracted(variant, price, fee, Scope.OWN_NETWORK, null, null);
    }

    private static ExtractedTariff foundAbove(String variant, String price, int kw) {
        return extracted(variant, price, "0", Scope.OWN_NETWORK, null, kw);
    }

    private static ExtractedTariff tier(String variant, String tier, String price) {
        return extracted(variant, price, "0", Scope.TIER, tier, null);
    }

    private static ExtractedTariff extracted(String variant, String price, String fee, Scope scope, String tier, Integer kw) {
        return new ExtractedTariff(variant, "DC", new BigDecimal(price), fee == null ? null : new BigDecimal(fee),
                null, null, null, null, scope, null, null, tier, kw, false, "Zitat " + price);
    }
}
