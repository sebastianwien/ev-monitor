package com.evmonitor.application;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.GeoHash;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient;
import com.evmonitor.infrastructure.external.ChargingStationRegistryClient.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * Säulen einer Geohash-6-Zelle für die Karte: ein Eintrag je Standort, nicht je Betreiber.
 */
class ChargingStationCellServiceTest {

    /** Hannover Kröpcke - öffentlicher Platz, keine Nutzerposition. */
    private static final double LAT = 52.3745;
    private static final double LON = 9.7385;
    private static final String CELL = GeoHash.withCharacterPrecision(LAT, LON, 6).toBase32();

    private ChargingStationRegistryClient registry;
    private ChargingStationCellService service;

    @BeforeEach
    void setUp() {
        registry = mock(ChargingStationRegistryClient.class);
        CpoRegistryMatcher matcher = new CpoRegistryMatcher(
                List.of("EnBW", "IONITY"),
                Map.of("enbw mobility+ ag und co.kg", "EnBW"));
        service = new ChargingStationCellService(registry, matcher,
                new ConcurrentMapCacheManager());
    }

    /** Ausstattung einer Einrichtung im Test. */
    private record Kit(Double ac, Double dc, int points, List<String> plugs) {}

    private static Station station(String operator, double lat, double lon, Kit kit, Integer registerId) {
        return new Station(operator, null, lat, lon, kit.ac(), kit.dc(), kit.points(), registerId,
                "Georgstraße", "1", "30159", "Hannover", kit.plugs(), null, null, "Ladekarte", "24/7");
    }

    private void registryReturns(Station... stations) {
        when(registry.findStationsInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(List.of(stations)));
    }

    @Test
    void fragtDasRechteckDerZelleAb() {
        registryReturns();

        service.stationsInCell(CELL);

        BoundingBox box = GeoHash.fromGeohashString(CELL).getBoundingBox();
        verify(registry).findStationsInBox(box.getSouthLatitude(), box.getWestLongitude(),
                box.getNorthLatitude(), box.getEastLongitude());
    }

    /** EnBW hat in Innenstädten mehrere Standorte je Zelle; jeder ist ein eigener Pin. */
    @Test
    void zweiStandorteDesselbenBetreibersBleibenZwei() {
        registryReturns(
                station("EnBW mobility+ AG und Co.KG", LAT, LON, new Kit(22.0, null, 2, List.of("Typ 2")), 1),
                station("EnBW mobility+ AG und Co.KG", LAT + 0.002, LON + 0.002, new Kit(null, 150.0, 2, List.of("CCS")), 2));

        var sites = service.stationsInCell(CELL).orElseThrow();

        assertThat(sites).hasSize(2);
        assertThat(sites).extracting(MapStation::name).containsOnly("EnBW");
    }

    @Test
    void einrichtungenEinesStandortsWerdenZusammengefuehrt() {
        registryReturns(
                station("EnBW mobility+ AG und Co.KG", LAT, LON, new Kit(22.0, null, 2, List.of("Typ 2")), 11),
                station("EnBW mobility+ AG und Co.KG", LAT + 0.0001, LON, new Kit(null, 150.0, 4, List.of("CCS", "Typ 2")), 12));

        var site = service.stationsInCell(CELL).orElseThrow().getFirst();

        assertThat(service.stationsInCell(CELL).orElseThrow()).hasSize(1);
        assertThat(site.id()).isEqualTo("11");
        assertThat(site.known()).isTrue();
        assertThat(site.maxAcKw()).isEqualTo(22.0);
        assertThat(site.maxDcKw()).isEqualTo(150.0);
        assertThat(site.chargePoints()).isEqualTo(6);
        assertThat(site.plugTypes()).containsExactly("Typ 2", "CCS");
        assertThat(site.latitude()).isEqualTo(LAT);
        assertThat(site.address()).isEqualTo("Georgstraße 1, 30159 Hannover");
        assertThat(site.payment()).isEqualTo("Ladekarte");
        assertThat(site.openingHours()).isEqualTo("24/7");
    }

    /** Verschiedene Betreiber am selben Platz sind verschiedene Pins: andere Preise. */
    @Test
    void verschiedeneBetreiberAmSelbenPlatzBleibenGetrennt() {
        registryReturns(
                station("EnBW mobility+ AG und Co.KG", LAT, LON, new Kit(null, 150.0, 2, List.of("CCS")), 1),
                station("IONITY GmbH", LAT, LON + 0.0001, new Kit(null, 350.0, 6, List.of("CCS")), 2));

        assertThat(service.stationsInCell(CELL).orElseThrow())
                .extracting(MapStation::name).containsExactly("EnBW", "IONITY");
    }

    /** Eine private Wallbox an der Straße ist kein Ladeort für andere. */
    @Test
    void privateEinzelWallboxFaelltWeg() {
        registryReturns(
                station("Erika Mustermann", LAT, LON, new Kit(11.0, null, 1, List.of("Typ 2")), 1),
                station("Hotel Am Markt GmbH", LAT + 0.003, LON, new Kit(22.0, null, 2, List.of("Typ 2")), 2));

        assertThat(service.stationsInCell(CELL).orElseThrow())
                .extracting(MapStation::name).containsExactly("Hotel Am Markt GmbH");
    }

    @Test
    void einrichtungOhnePositionFaelltWeg() {
        registryReturns(new Station("IONITY GmbH", null, null, null, null, 350.0, 6, 3,
                null, null, null, null, List.of(), null, null, null, null));

        assertThat(service.stationsInCell(CELL).orElseThrow()).isEmpty();
    }

    @Test
    void ohneRegisterIDTraegtDerStandortEineStabileErsatzID() {
        registryReturns(station("IONITY GmbH", LAT, LON, new Kit(null, 350.0, 6, List.of("CCS")), null));

        String id = service.stationsInCell(CELL).orElseThrow().getFirst().id();

        assertThat(id).isNotBlank();
        assertThat(service.stationsInCell(CELL).orElseThrow().getFirst().id()).isEqualTo(id);
    }

    @Test
    void registerAusfallIstLeeresOptional() {
        when(registry.findStationsInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(Optional.empty());

        assertThat(service.stationsInCell(CELL)).isEmpty();
    }

    @Test
    void ungueltigeZelleFragtNichtAn() {
        assertThat(service.stationsInCell("u1qcv")).isEmpty();
        assertThat(service.stationsInCell("u1qcva")).isEmpty();
        assertThat(service.stationsInCell(null)).isEmpty();
        verifyNoInteractions(registry);
    }

    @Test
    void zellformatNurSechsStellenGeohash() {
        assertThat(ChargingStationCellService.isValidCell("u1qcvx")).isTrue();
        assertThat(ChargingStationCellService.isValidCell("U1QCVX")).isFalse();
        assertThat(ChargingStationCellService.isValidCell("u1qcvxa")).isFalse();
        assertThat(ChargingStationCellService.isValidCell("u1qcvi")).isFalse();
    }
}
