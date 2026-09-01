package com.evmonitor.application;

import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Setzt Registerabfrage und Namensabgleich zusammen.
 *
 * <p>Datenschutz: der Dienst bekommt nie die Position des Nutzers, sondern den
 * Mittelpunkt der Geohash-Zelle, in der geladen wurde.
 */
class NearbyCpoServiceTest {

    private ChargingStationRegistryClient registry;
    private ChargingProviderTariffRepository tariffRepository;
    private NearbyCpoService service;

    @BeforeEach
    void setUp() {
        registry = mock(ChargingStationRegistryClient.class);
        tariffRepository = mock(ChargingProviderTariffRepository.class);
        CpoRegistryMatcher matcher = new CpoRegistryMatcher(
                List.of("Allego", "IONITY", "EnBW"),
                Map.of("enbw mobility+ ag und co.kg", "EnBW"));
        service = new NearbyCpoService(registry, matcher, 250);
    }

    /** Nicht die Nutzerposition, sondern der Zellmittelpunkt geht an den fremden Dienst. */
    @Test
    void fragtMitDemMittelpunktDerGeohashZelleAn() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of()));

        service.findNearbyCpos("u33dc0c");

        ArgumentCaptor<Double> lat = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lon = ArgumentCaptor.forClass(Double.class);
        verify(registry).findStationsNearby(lat.capture(), lon.capture(), eq(250));
        assertThat(lat.getValue()).isCloseTo(52.52, org.assertj.core.data.Offset.offset(0.01));
        assertThat(lon.getValue()).isCloseTo(13.40, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void liefertDieKanonischenNamenDerUmgebung() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(
                new Station("Allego GmbH", "Allego"),
                new Station("EnBW mobility+ AG und Co.KG", null),
                new Station("Maike Schaper", null))));

        assertThat(service.findNearbyCpos("u33dc0c")).contains(List.of("Allego", "EnBW"));
    }

    /** "Dort steht nichts" ist eine Antwort und darf gecacht werden. */
    @Test
    void keineTrefferLiefertEineLeereListe() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of()));

        assertThat(service.findNearbyCpos("u33dc0c")).contains(List.of());
    }

    /** Ein Ausfall des Registers darf nicht als Ergebnis durchgehen - sonst friert er im Cache ein. */
    @Test
    void ausfallDesRegistersLiefertKeinErgebnis() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.empty());

        assertThat(service.findNearbyCpos("u33dc0c")).isEmpty();
    }

    @Test
    void ungueltigerGeohashFragtNichtAnUndLiefertLeer() {
        assertThat(service.findNearbyCpos("nicht-valide!")).isEmpty();
        assertThat(service.findNearbyCpos(null)).isEmpty();
        assertThat(service.findNearbyCpos("")).isEmpty();
        verifyNoInteractions(registry);
    }
}
