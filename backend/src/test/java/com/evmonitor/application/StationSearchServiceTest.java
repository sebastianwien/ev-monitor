package com.evmonitor.application;

import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Textsuche im Ladesaeulenregister fuer das Log-Formular: "EnBW Lichtenau" soll die Saeule
 * als Kachel liefern, je Betreiber und Standort eine, ohne private Wallboxen.
 */
class StationSearchServiceTest {

    private ChargingStationRegistryClient registry;
    private StationSearchService service;

    @BeforeEach
    void setUp() {
        registry = mock(ChargingStationRegistryClient.class);
        CpoRegistryMatcher matcher = new CpoRegistryMatcher(
                List.of("EnBW", "IONITY"), Map.of("enbw mobility+ ag und co.kg", "EnBW"));
        service = new StationSearchService(registry, matcher);
    }

    private static Station enbw(double lat, double lon, String city, double dcKw, int points) {
        return new Station("EnBW mobility+ AG und Co.KG", null, lat, lon, null, dcKw, points,
                null, "Am Fuchsgraben", "1", null, city, List.of("CCS"), null, null, null, null);
    }

    /** Zwei Saeulen am selben Standort sind eine Kachel: Leistung als Maximum, Ladepunkte als Summe. */
    @Test
    void fasstSaeulenEinesBetreibersAmSelbenStandortZusammen() {
        when(registry.searchStations(anyString())).thenReturn(Optional.of(List.of(
                enbw(49.2809, 10.7121, "Lichtenau", 150, 2),
                enbw(49.2810, 10.7122, "Lichtenau", 300, 2))));

        List<StationMatch> matches = service.search("enbw lichtenau").orElseThrow();

        assertThat(matches).hasSize(1);
        StationMatch m = matches.getFirst();
        assertThat(m.name()).isEqualTo("EnBW");
        assertThat(m.known()).isTrue();
        assertThat(m.maxDcKw()).isEqualTo(300.0);
        assertThat(m.chargePoints()).isEqualTo(4);
        assertThat(m.geohash()).hasSize(7);
        assertThat(m.address()).isEqualTo("Am Fuchsgraben 1, Lichtenau");
    }

    /** Lichtenau gibt es in Bayern und Sachsen: gleicher Betreiber, andere Zelle, zwei Kacheln. */
    @Test
    void haeltStandorteDesselbenBetreibersInVerschiedenenOrtenAuseinander() {
        when(registry.searchStations(anyString())).thenReturn(Optional.of(List.of(
                enbw(49.2809, 10.7121, "Lichtenau", 150, 2),
                enbw(50.9800, 12.9900, "Lichtenau", 150, 4))));

        List<StationMatch> matches = service.search("enbw lichtenau").orElseThrow();

        assertThat(matches).hasSize(2)
                .extracting(StationMatch::name, StationMatch::chargePoints)
                .containsExactly(tuple("EnBW", 2), tuple("EnBW", 4));
    }

    @Test
    void begrenztAufFuenfStandorteInRegisterReihenfolge() {
        List<Station> many = IntStream.range(0, 8)
                .mapToObj(i -> enbw(49.0 + i, 10.0, "Ort" + i, 150, 2)).toList();
        when(registry.searchStations(anyString())).thenReturn(Optional.of(many));

        List<StationMatch> matches = service.search("enbw").orElseThrow();

        assertThat(matches).hasSize(NearbyCpoService.MAX_STATIONS);
        assertThat(matches.getFirst().city()).isEqualTo("Ort0");
    }

    /** Privatperson mit einem AC-Ladepunkt an der Strasse ist kein Ladeort fuer andere. */
    @Test
    void laesstPrivateWallboxenUndEintraegeOhnePositionWeg() {
        when(registry.searchStations(anyString())).thenReturn(Optional.of(List.of(
                new Station("Maike Schaper", null, 49.0, 10.0, 11.0, false, 1),
                new Station("IONITY GmbH", "IONITY", null, null, 350.0, true, 4),
                new Station("Stadtwerke X", null, 49.1, 10.1, 22.0, false, 2))));

        List<StationMatch> matches = service.search("x").orElseThrow();

        assertThat(matches).extracting(StationMatch::name, StationMatch::known)
                .containsExactly(tuple("Stadtwerke X", false));
    }

    @Test
    void ausfallDesRegistersIstKeinErgebnis() {
        when(registry.searchStations(anyString())).thenReturn(Optional.empty());

        assertThat(service.search("enbw")).isEmpty();
    }
}
