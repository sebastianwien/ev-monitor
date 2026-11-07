package com.evmonitor.application.tariff;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.persistence.EmpEntity;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Eine Stelle für "welche Katalogtarife gelten an Betreiber X, Ladeart Y, Leistung Z am Tag D".
 */
class TariffCatalogTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    private ChargingProviderTariffRepository tariffs;
    private JpaEmpRepository emps;
    private TariffCatalog catalog;
    private final List<ChargingProviderTariff> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tariffs = mock(ChargingProviderTariffRepository.class);
        emps = mock(JpaEmpRepository.class);
        when(tariffs.findValidOn(any())).thenReturn(rows);
        when(tariffs.findTierForCpo(anyString(), anyString())).thenReturn(Optional.empty());
        when(emps.findAll()).thenReturn(List.of(emp("Smatrics", "AT"), emp("EnBW", "DE"), emp("Maingau", null)));
        catalog = new TariffCatalog(tariffs, emps);
    }

    private static EmpEntity emp(String name, String country) {
        EmpEntity e = new EmpEntity();
        e.setName(name);
        e.setCountryCode(country);
        return e;
    }

    /** Eigenes Netz: Kartenname gleich Betreiber, kein appliesElsewhere. */
    private static ChargingProviderTariff.ChargingProviderTariffBuilder row(String emp, String price) {
        return ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp)
                .chargingType(ChargingType.DC).pricePerKwh(new BigDecimal(price))
                .sessionFeeEur(BigDecimal.ZERO).validFrom(LocalDate.of(2025, 1, 1));
    }

    @Test
    void fuerDeutschlandFallenDynamischeUndAuslaendischeAnbieterWeg() {
        rows.add(row("Smatrics", "0.45").appliesElsewhere(true).build());
        rows.add(row("Tesla", "0.40").appliesElsewhere(true).dynamicPricing(true).build());
        rows.add(row("Maingau", "0.62").appliesElsewhere(true).build());
        rows.add(row("EnBW", "0.59").build());

        var at = catalog.forGermanyOn(DAY).at("EnBW", ChargingType.DC, null);

        assertThat(at).extracting(ChargingProviderTariff::getEmpName).containsExactly("EnBW", "Maingau");
    }

    /** Die bestehenden Vergleiche (Story, Ladeorte) gelten auch im Ausland: dort bleibt der Anbieter. */
    @Test
    void ohneLandfilterBleibenAuslaendischeAnbieter() {
        rows.add(row("Smatrics", "0.45").appliesElsewhere(true).build());
        rows.add(row("Tesla", "0.40").appliesElsewhere(true).dynamicPricing(true).build());

        var at = catalog.on(DAY).at("EnBW", ChargingType.DC, null);

        assertThat(at).extracting(ChargingProviderTariff::getEmpName).containsExactly("Smatrics");
    }

    @Test
    void guenstigsterZuerstBeiGleichemPreisNachName() {
        rows.add(row("Maingau", "0.62").appliesElsewhere(true).build());
        rows.add(row("ADAC", "0.62").appliesElsewhere(true).build());
        rows.add(row("EnBW", "0.39").tariffVariant("L").build());

        var at = catalog.forGermanyOn(DAY).at("EnBW", ChargingType.DC, null);

        assertThat(at).extracting(ChargingProviderTariff::getEmpName).containsExactly("EnBW", "ADAC", "Maingau");
    }

    /** An der Säule ist die Leistung aus dem Register bekannt: die passende kW-Stufe gilt. */
    @Test
    void leistungWaehltDieKwStufe() {
        rows.add(row("EnBW", "0.59").build());
        rows.add(row("EnBW", "0.69").minPowerKw(50).build());

        var snapshot = catalog.forGermanyOn(DAY);

        assertThat(snapshot.at("EnBW", ChargingType.DC, 150)).extracting(ChargingProviderTariff::getPricePerKwh)
                .containsExactly(new BigDecimal("0.69"));
        assertThat(snapshot.at("EnBW", ChargingType.DC, 50)).extracting(ChargingProviderTariff::getPricePerKwh)
                .containsExactly(new BigDecimal("0.59"));
        assertThat(snapshot.at("EnBW", ChargingType.DC, null)).extracting(ChargingProviderTariff::getPricePerKwh)
                .containsExactly(new BigDecimal("0.69"));
    }

    /** Eine Karte mit vielen Säulen fragt das Tier-Mapping je Anbieter und Betreiber nur einmal. */
    @Test
    void tierAbfrageJeAnbieterUndBetreiberEinmal() {
        rows.add(row("Maingau", "0.52").priceTier("LOW").build());
        rows.add(row("Maingau", "0.72").priceTier("STANDARD").appliesElsewhere(true).build());
        when(tariffs.findTierForCpo("Maingau", "EWE Go")).thenReturn(Optional.of("LOW"));

        var snapshot = catalog.forGermanyOn(DAY);
        snapshot.at("EWE Go", ChargingType.DC, null);
        var again = snapshot.at("EWE Go", ChargingType.DC, null);
        snapshot.at("Allego", ChargingType.DC, null);

        assertThat(again).extracting(ChargingProviderTariff::getPricePerKwh).containsExactly(new BigDecimal("0.52"));
        verify(tariffs, times(1)).findTierForCpo("Maingau", "EWE Go");
        verify(tariffs, times(1)).findTierForCpo("Maingau", "Allego");
        verify(tariffs, times(1)).findValidOn(DAY);
    }

    @Test
    void ohneBetreiberOderLadeartKeineTarife() {
        rows.add(row("Maingau", "0.62").appliesElsewhere(true).build());

        var snapshot = catalog.forGermanyOn(DAY);

        assertThat(snapshot.at(null, ChargingType.DC, null)).isEmpty();
        assertThat(snapshot.at("EnBW", null, null)).isEmpty();
    }
}
