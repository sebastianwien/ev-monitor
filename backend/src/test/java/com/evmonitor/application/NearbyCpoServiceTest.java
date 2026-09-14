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
import static org.assertj.core.api.Assertions.tuple;
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

    // --- Standortvorschlaege fuer das Log-Formular ---

    private static Station at(String operator, String brand, double lat, double lon, double kw, boolean fast, int points) {
        return new Station(operator, brand, lat, lon, kw, fast, points);
    }

    /**
     * Der Vorschlag traegt die Leistung je Ladeart (Maximum ueber die Saeulen des Betreibers)
     * und die Registerdaten der naechsten Saeule: ID, Adresse, Stecker.
     */
    @Test
    void traegtLeistungJeLadeartUndRegisterdatenDerNaechstenSaeule() {
        Station near = new Station("Kaufland Dienstleistung GmbH & Co. KG", "Kaufland", 52.5196, 13.4055, 43.0, 50.0, 2,
                1064145, "Storkower Str.", "139", "10407", "Berlin", List.of("Typ 2", "CCS"),
                java.time.LocalDate.of(2018, 11, 27), "3330", "Onlinezahlungsverfahren", "24/7");
        Station far = new Station("Kaufland Dienstleistung GmbH & Co. KG", "Kaufland", 52.5212, 13.4054, 22.0, 150.0, 4,
                1064146, "Storkower Str.", "141", "10407", "Berlin", List.of("CCS"), null, null, null, null);
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(far, near)));

        var s = service.findNearbyStations("u33dc0c").orElseThrow().getFirst();

        assertThat(s.maxAcKw()).isEqualTo(43.0);
        assertThat(s.maxDcKw()).isEqualTo(150.0);
        assertThat(s.chargePoints()).isEqualTo(6);
        assertThat(s.registerId()).isEqualTo(1064145);
        assertThat(s.address()).isEqualTo("Storkower Str. 139, 10407 Berlin");
        assertThat(s.plugTypes()).containsExactly("Typ 2", "CCS");
    }

    @Test
    void sortiertStandorteNachEntfernungZumZellmittelpunkt() {
        // Zelle u33dc0c: Mittelpunkt 52.51945, 13.40538
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(
                at("Allego GmbH", "Allego", 52.5212, 13.4054, 150, true, 4),
                at("IONITY GmbH", "IONITY", 52.5196, 13.4055, 350, true, 6))));

        var stations = service.findNearbyStations("u33dc0c").orElseThrow();

        assertThat(stations).extracting(NearbyStation::name).containsExactly("IONITY", "Allego");
        assertThat(stations.getFirst().distanceMeters()).isLessThan(30);
        assertThat(stations.get(1).distanceMeters()).isBetween(150, 250);
    }

    /**
     * Fuer die Wiedererkennung als Ladestandort (charging_site) traegt jeder Vorschlag die
     * Geohash-Zelle der naechstgelegenen Saeule seines Betreibers - nicht die des Nutzers.
     */
    @Test
    void traegtDieZelleDerNaechstenSaeuleDesBetreibers() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(
                at("IONITY GmbH", "IONITY", 52.5212, 13.4054, 350, true, 2),
                at("IONITY GmbH", "IONITY", 52.5196, 13.4055, 350, true, 2))));

        var stations = service.findNearbyStations("u33dc0c").orElseThrow();

        String nearest = ch.hsr.geohash.GeoHash.withCharacterPrecision(52.5196, 13.4055, 7).toBase32();
        assertThat(stations).hasSize(1);
        assertThat(stations.getFirst().geohash()).isEqualTo(nearest);
    }

    /**
     * Das Register meldet jede Saeule einzeln, oft an mehreren Punkten desselben Parkplatzes.
     * Fuer die Auswahl im Formular zaehlt der Betreiber, nicht die Saeule: ein Eintrag je Name,
     * mit der kuerzesten Entfernung, der hoechsten Leistung und allen Ladepunkten.
     */
    @Test
    void fasstSaeulenDesselbenBetreibersZusammen() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(
                at("IONITY GmbH", "IONITY", 52.5196, 13.4055, 350, true, 2),
                at("IONITY GmbH", "IONITY", 52.5196, 13.4055, 350, true, 2),
                at("IONITY GmbH", "IONITY", 52.5205, 13.4060, 400, true, 2))));

        var stations = service.findNearbyStations("u33dc0c").orElseThrow();

        assertThat(stations).hasSize(1);
        assertThat(stations.getFirst().chargePoints()).isEqualTo(6);
        assertThat(stations.getFirst().maxPowerKw()).isEqualTo(400.0);
        assertThat(stations.getFirst().distanceMeters()).isLessThan(30);
        assertThat(stations.getFirst().fastCharging()).isTrue();
    }

    /**
     * Im Register stehen auch Privatpersonen mit einer 11-kW-Wallbox an der Strasse. Die sind
     * fuer niemanden ein Ladeort und ihre Namen haben im Formular nichts verloren. Erkennbar
     * sind sie nur an der Groesse: unbekannter Betreiber, ein Normal-Ladepunkt.
     */
    @Test
    void unbekannteEinzelWallboxenFallenWeg() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(
                at("Maike Schaper", null, 52.5196, 13.4055, 11, false, 1),
                at("Hotel Adlon GmbH", null, 52.5196, 13.4056, 22, false, 2),
                at("Allego GmbH", "Allego", 52.5197, 13.4055, 11, false, 1))));

        var stations = service.findNearbyStations("u33dc0c").orElseThrow();

        assertThat(stations).extracting(NearbyStation::name).containsExactly("Hotel Adlon GmbH", "Allego");
    }

    /**
     * Anders als bei der Namensliste faellt ein unbekannter Betreiber hier nicht weg:
     * das waere genau die Saeule, an der der Nutzer gerade steht.
     */
    @Test
    void unbekannteBetreiberBleibenMitRohnamenErhalten() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(
                at("Stadtwerke Musterstadt GmbH", null, 52.5204, 13.4046, 22, false, 2),
                at("EnBW mobility+ AG und Co.KG", null, 52.5205, 13.4046, 300, true, 4))));

        var stations = service.findNearbyStations("u33dc0c").orElseThrow();

        assertThat(stations).extracting(NearbyStation::name, NearbyStation::known)
                .containsExactly(tuple("Stadtwerke Musterstadt GmbH", false), tuple("EnBW", true));
    }

    @Test
    void bevorzugtDenAnzeigenamenVorDemBetreiberBeiUnbekannten() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(
                at("BP Europa SE", "Aral pulse", 52.5204, 13.4046, 300, true, 4))));

        assertThat(service.findNearbyStations("u33dc0c").orElseThrow().getFirst().name()).isEqualTo("Aral pulse");
    }

    @Test
    void eintraegeOhnePositionKoennenNichtVorgeschlagenWerden() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(List.of(
                new Station("Allego GmbH", "Allego"))));

        assertThat(service.findNearbyStations("u33dc0c")).contains(List.of());
    }

    @Test
    void begrenztDieVorschlaegeAufDieNaechstenFuenf() {
        var many = new java.util.ArrayList<Station>();
        for (int i = 0; i < 8; i++) {
            many.add(at("Betreiber " + i, null, 52.5204 + i * 0.0002, 13.4046, 22, false, 2));
        }
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(many));

        assertThat(service.findNearbyStations("u33dc0c").orElseThrow()).hasSize(5);
    }

    @Test
    void ausfallDesRegistersIstBeiStandortenEinLeeresOptional() {
        when(registry.findStationsNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.empty());

        assertThat(service.findNearbyStations("u33dc0c")).isEmpty();
    }
}
