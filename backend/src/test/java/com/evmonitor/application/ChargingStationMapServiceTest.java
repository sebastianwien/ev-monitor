package com.evmonitor.application;

import com.evmonitor.application.tariff.TariffCatalog;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
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
 * Setzt Kartenzellen und Katalogpreise zusammen: je Säule der günstigste Preis je Ladeart.
 */
class ChargingStationMapServiceTest {

    /** Hannover Mitte und eine Zelle in Wien. */
    private static final String HANNOVER = "u1qcvx";
    private static final String VIENNA = "u2edk8";

    private ChargingStationCellService cells;
    private ChargingProviderTariffRepository tariffRepository;
    private ChargingStationMapService service;
    private final List<ChargingProviderTariff> tariffs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cells = mock(ChargingStationCellService.class);
        tariffRepository = mock(ChargingProviderTariffRepository.class);
        JpaEmpRepository emps = mock(JpaEmpRepository.class);
        when(tariffRepository.findValidOn(any())).thenReturn(tariffs);
        when(tariffRepository.findTierForCpo(anyString(), anyString())).thenReturn(Optional.empty());
        when(emps.findAll()).thenReturn(List.of());
        when(cells.isCached(anyString())).thenReturn(true);
        service = new ChargingStationMapService(cells, new TariffCatalog(tariffRepository, emps));
    }

    private static MapStation site(String id, String name, boolean known, Double ac, Double dc) {
        return new MapStation(id, name, known, 52.3745, 9.7385, ac, dc, 4, List.of("CCS"),
                "Georgstraße", "1", "30159", "Hannover", "24/7", "Ladekarte");
    }

    private static ChargingProviderTariff.ChargingProviderTariffBuilder tariff(String emp, ChargingType type, String price) {
        return ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp).chargingType(type)
                .pricePerKwh(new BigDecimal(price)).sessionFeeEur(BigDecimal.ZERO)
                .monthlyFeeEur(BigDecimal.ZERO).validFrom(LocalDate.of(2025, 1, 1));
    }

    @Test
    void jeSaeuleDerGuenstigstePreisJeLadeartMitKartenname() {
        when(cells.stationsInCell(HANNOVER)).thenReturn(Optional.of(List.of(site("1", "EnBW", true, 22.0, 150.0))));
        tariffs.add(tariff("EnBW", ChargingType.DC, "0.59").build());
        tariffs.add(tariff("EnBW", ChargingType.DC, "0.39").tariffVariant("L").monthlyFeeEur(new BigDecimal("11.99")).build());
        tariffs.add(tariff("EnBW", ChargingType.AC, "0.49").build());
        tariffs.add(tariff("Maingau", ChargingType.DC, "0.72").appliesElsewhere(true).build());

        var station = service.stations(List.of(HANNOVER), cell -> true).cells().getFirst().stations().getFirst();

        assertThat(station.priceDc().pricePerKwh()).isEqualByComparingTo("0.39");
        assertThat(station.priceDc().cardName()).isEqualTo("EnBW");
        assertThat(station.priceDc().variant()).isEqualTo("L");
        assertThat(station.priceAc().pricePerKwh()).isEqualByComparingTo("0.49");
        assertThat(station.name()).isEqualTo("EnBW");
        assertThat(station.address()).isEqualTo("Georgstraße 1, 30159 Hannover");
    }

    /** Eine reine Schnellladesäule hat keinen AC-Preis, auch wenn die Karte einen kennt. */
    @Test
    void preisNurFuerLadeartenDieDieSaeuleHat() {
        when(cells.stationsInCell(HANNOVER)).thenReturn(Optional.of(List.of(site("1", "IONITY", true, null, 350.0))));
        tariffs.add(tariff("Maingau", ChargingType.AC, "0.52").appliesElsewhere(true).build());
        tariffs.add(tariff("Maingau", ChargingType.DC, "0.72").appliesElsewhere(true).build());

        var station = service.stations(List.of(HANNOVER), cell -> true).cells().getFirst().stations().getFirst();

        assertThat(station.priceAc()).isNull();
        assertThat(station.priceDc().cardName()).isEqualTo("Maingau");
    }

    /** Ein unbekannter Betreiber ist im Katalog nicht belegt: Pin ohne Preis. */
    @Test
    void unbekannterBetreiberOhnePreis() {
        when(cells.stationsInCell(HANNOVER)).thenReturn(Optional.of(List.of(site("1", "Stadtwerke Musterstadt", false, 22.0, null))));
        tariffs.add(tariff("Maingau", ChargingType.AC, "0.52").appliesElsewhere(true).build());

        var station = service.stations(List.of(HANNOVER), cell -> true).cells().getFirst().stations().getFirst();

        assertThat(station.priceAc()).isNull();
        assertThat(station.priceDc()).isNull();
    }

    @Test
    void ohneDrosselfreigabeStehtDieZelleInMissing() {
        when(cells.isCached(HANNOVER)).thenReturn(false);

        var map = service.stations(List.of(HANNOVER), cell -> false);

        assertThat(map.cells()).isEmpty();
        assertThat(map.missing()).containsExactly(HANNOVER);
        verify(cells, never()).stationsInCell(anyString());
    }

    /** Gecachte Zellen kosten das Register nichts und zählen nicht gegen die Drossel. */
    @Test
    void gecachteZelleFragtDieDrosselNicht() {
        when(cells.stationsInCell(HANNOVER)).thenReturn(Optional.of(List.of()));
        List<String> asked = new ArrayList<>();

        service.stations(List.of(HANNOVER), cell -> asked.add(cell));

        assertThat(asked).isEmpty();
    }

    @Test
    void registerAusfallStehtInMissing() {
        when(cells.stationsInCell(HANNOVER)).thenReturn(Optional.empty());

        var map = service.stations(List.of(HANNOVER), cell -> true);

        assertThat(map.missing()).containsExactly(HANNOVER);
        assertThat(map.cells()).isEmpty();
    }

    /** Für den Hinweis "Säulen und Preise gibt es für Deutschland". */
    @Test
    void zelleTraegtObSieInDeutschlandLiegt() {
        when(cells.stationsInCell(anyString())).thenReturn(Optional.of(List.of()));

        var map = service.stations(List.of(HANNOVER, VIENNA), cell -> true);

        assertThat(map.cells()).extracting(ChargingStationMap.Cell::cell, ChargingStationMap.Cell::inGermany)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(HANNOVER, true),
                        org.assertj.core.groups.Tuple.tuple(VIENNA, false));
    }

    /** Der Katalog wird je Anfrage einmal geladen, nicht je Säule. */
    @Test
    void katalogEinmalJeAnfrage() {
        when(cells.stationsInCell(anyString())).thenReturn(Optional.of(List.of(
                site("1", "EnBW", true, 22.0, 150.0), site("2", "EnBW", true, 22.0, 150.0))));

        service.stations(List.of(HANNOVER, "u1qcvz"), cell -> true);

        verify(tariffRepository, times(1)).findValidOn(any());
    }
}
