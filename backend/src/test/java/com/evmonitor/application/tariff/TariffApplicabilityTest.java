package com.evmonitor.application.tariff;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which row of a card applies at one operator: the most specific one per card and variant.
 * Named operator beats tier, tier beats own network, own network beats "everywhere else".
 * Several DC rows by power: the one for the charge's power, without power the dearest one,
 * so a saving is never overstated.
 */
class TariffApplicabilityTest {

    private static final Map<String, String> MAINGAU_TIERS = Map.of("IONITY", "LOW", "EnBW", "HIGH");
    private static final TariffApplicability.TierLookup TIERS =
            (emp, operator) -> emp.equals("Maingau") ? Optional.ofNullable(MAINGAU_TIERS.get(operator)) : Optional.empty();

    @Test
    void ownNetworkRow_appliesOnlyAtTheProvidersOwnOperator() {
        var enbw = row("EnBW", "Tariff L").price("0.39").build();

        assertThat(at(List.of(enbw), "EnBW", null)).containsExactly(enbw);
        assertThat(at(List.of(enbw), "IONITY", null)).isEmpty();
    }

    @Test
    void namedOperator_beatsElsewhere_forTheSameCardAndVariant() {
        var atAral = row("ADAC e-Charge", "Standard").cpo("Aral Pulse").price("0.55").build();
        var elsewhere = row("ADAC e-Charge", "Standard").elsewhere().price("0.75").build();

        assertThat(at(List.of(atAral, elsewhere), "Aral Pulse", null)).containsExactly(atAral);
        assertThat(at(List.of(atAral, elsewhere), "IONITY", null)).containsExactly(elsewhere);
    }

    @Test
    void mappedTier_beatsTheElsewhereTier() {
        var low = row("Maingau", "Standard").tier("LOW").price("0.62").build();
        var standard = row("Maingau", "Standard").tier("STANDARD").elsewhere().price("0.72").build();
        var high = row("Maingau", "Standard").tier("HIGH").price("0.82").build();
        var all = List.of(low, standard, high);

        assertThat(at(all, "IONITY", null)).containsExactly(low);
        assertThat(at(all, "EnBW", null)).containsExactly(high);
        assertThat(at(all, "Tesla Supercharger", null)).containsExactly(standard);
    }

    @Test
    void unmappedTier_withoutElsewhere_appliesNowhere() {
        var high = row("Maingau", "Standard").tier("HIGH").price("0.82").build();

        assertThat(at(List.of(high), "Tesla Supercharger", null)).isEmpty();
    }

    @Test
    void ownNetwork_beatsElsewhere() {
        var own = row("Aral Pulse", "Klassik").price("0.62").build();
        var roaming = row("Aral Pulse", "Klassik").elsewhere().price("0.79").build();

        assertThat(at(List.of(own, roaming), "Aral Pulse", null)).containsExactly(own);
        assertThat(at(List.of(own, roaming), "EnBW", null)).containsExactly(roaming);
    }

    @Test
    void eachVariantKeepsItsOwnRow() {
        var klassik = row("Aral Pulse", "Klassik").price("0.62").build();
        var extra = row("Aral Pulse", "Extra").price("0.54").build();

        assertThat(at(List.of(klassik, extra), "Aral Pulse", null)).containsExactlyInAnyOrder(klassik, extra);
    }

    @Test
    void powerRows_pickTheClassOfTheCharge() {
        var upTo50 = row("Aral Pulse", "Klassik").price("0.52").build();
        var above50 = row("Aral Pulse", "Klassik").minPowerKw(50).price("0.62").build();
        var all = List.of(upTo50, above50);

        assertThat(at(all, "Aral Pulse", 48)).containsExactly(upTo50);
        assertThat(at(all, "Aral Pulse", 50)).containsExactly(upTo50);
        assertThat(at(all, "Aral Pulse", 150)).containsExactly(above50);
    }

    @Test
    void powerRows_withoutKnownPower_takeTheDearestOne() {
        var upTo50 = row("Aral Pulse", "Klassik").price("0.52").build();
        var above50 = row("Aral Pulse", "Klassik").minPowerKw(50).price("0.62").build();

        assertThat(at(List.of(upTo50, above50), "Aral Pulse", null)).containsExactly(above50);
    }

    @Test
    void onlyAboveAPower_doesNotApplyToASlowerCharge() {
        var hpc = row("Vattenfall InCharge", "Standard").minPowerKw(50).price("0.49").build();

        assertThat(at(List.of(hpc), "Vattenfall InCharge", 22)).isEmpty();
        assertThat(at(List.of(hpc), "Vattenfall InCharge", 150)).containsExactly(hpc);
    }

    @Test
    void otherChargingType_neverApplies() {
        var ac = row("EnBW", "Tariff L").type(ChargingType.AC).price("0.39").build();

        assertThat(at(List.of(ac), "EnBW", null)).isEmpty();
    }

    private static List<ChargingProviderTariff> at(List<ChargingProviderTariff> tariffs, String operator, Integer powerKw) {
        return TariffApplicability.applicable(tariffs, new TariffApplicability.Charge(operator, ChargingType.DC, powerKw), TIERS);
    }

    private static Row row(String emp, String variant) {
        return new Row(ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp).tariffVariant(variant)
                .chargingType(ChargingType.DC).sessionFeeEur(BigDecimal.ZERO).monthlyFeeEur(BigDecimal.ZERO)
                .validFrom(LocalDate.of(2026, 1, 1)));
    }

    private record Row(ChargingProviderTariff.ChargingProviderTariffBuilder b) {
        Row cpo(String cpo) { b.cpoName(cpo); return this; }
        Row tier(String tier) { b.priceTier(tier); return this; }
        Row elsewhere() { b.appliesElsewhere(true); return this; }
        Row minPowerKw(int kw) { b.minPowerKw(kw); return this; }
        Row type(ChargingType t) { b.chargingType(t); return this; }
        Row price(String p) { b.pricePerKwh(new BigDecimal(p)); return this; }
        ChargingProviderTariff build() { return b.build(); }
    }
}
