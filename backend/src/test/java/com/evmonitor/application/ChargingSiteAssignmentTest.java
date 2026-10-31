package com.evmonitor.application;

import com.evmonitor.application.ingest.ChargingEntry;
import com.evmonitor.application.ingest.IngestCommand;
import com.evmonitor.application.ingest.IngestDoor;
import com.evmonitor.application.ingest.IngestGateway;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingSiteAttachment;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.weather.TemperatureSource;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Automatisch eingehende Ladungen bekommen ihre Register-Säule nach dem Speichern: Ort, Betreiber
 * und "öffentlich" kommen von der Säule, Preis und Karte danach aus der Historie an dieser Säule.
 *
 * Ausgangsfall: Die Säule liegt laut Register in einer Zelle, das Auto parkt in der Nachbarzelle.
 * Ohne Zuordnung fand die Preislogik dort kein AC-Vorbild, und die Ladung blieb ohne Preis.
 */
class ChargingSiteAssignmentTest extends AbstractIntegrationTest {

    private static final String SITE_CELL = "u0v9zq4";
    private static final String NEIGHBOUR_CELL = "u0v9zq5";
    private static final NearbyStation SUPERMARKT = new NearbyStation("Supermarkt", true, 60, 43.0, 50.0, 2, SITE_CELL,
            null, null, null, null, null, null, null, List.of("Typ 2", "CCS"), null, null, null, null);

    @Autowired private IngestGateway gateway;
    @Autowired private ChargingSiteService chargingSiteService;
    @MockitoBean private NearbyCpoService nearbyCpoService;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("site-assign-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(SUPERMARKT)));
    }

    @Test
    void ladungInDerNachbarzelleBekommtSäuleOrtUndPreisAusDerHistorie() {
        EvLog anchor = ingest(acCharge(LocalDateTime.of(2026, 10, 26, 19, 28), SITE_CELL)
                .publicCharging(true).kwhCharged(new BigDecimal("43.92"))
                .costEur(new BigDecimal("12.74")).pricePerKwh(new BigDecimal("0.29")).build());

        EvLog live = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL)
                .publicCharging(true).kwhCharged(new BigDecimal("46.18"))
                .maxChargingPowerKw(new BigDecimal("11.4")).build());

        assertThat(live.getChargingSiteId()).isNotNull().isEqualTo(anchor.getChargingSiteId());
        assertThat(live.getGeohash()).isEqualTo(SITE_CELL);
        assertThat(live.getCpoName()).isEqualTo("Supermarkt");
        assertThat(live.getPricePerKwh()).isEqualByComparingTo("0.29");
        assertThat(live.getCostEur()).isEqualByComparingTo("13.39");
    }

    @Test
    void unbekannterLadeortWirdMitDerSäuleÖffentlich() {
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).build());

        assertThat(log.getChargingSiteId()).isNotNull();
        assertThat(log.getPublicCharging()).isTrue();
    }

    @Test
    void betreiberDerQuelleBleibtStehen() {
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL)
                .publicCharging(true).cpoName("Stadtwerke").build());

        assertThat(log.getChargingSiteId()).isNotNull();
        assertThat(log.getCpoName()).isEqualTo("Stadtwerke");
    }

    @Test
    void privateLadungBleibtOhneSäuleUndFragtDasRegisterNicht() {
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL)
                .publicCharging(false).build());

        assertThat(log.getChargingSiteId()).isNull();
        verify(nearbyCpoService, never()).findNearbyStations(anyString());
    }

    @Test
    void wallboxLadungBleibtOhneSäule() {
        EvLog log = ingest(DataSource.WALLBOX_GOE, acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).build());

        assertThat(log.getChargingSiteId()).isNull();
        verify(nearbyCpoService, never()).findNearbyStations(anyString());
    }

    @Test
    void registerAusfallLässtDieLadungUnverändert() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());

        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).publicCharging(true).build());

        assertThat(log.getChargingSiteId()).isNull();
        assertThat(log.getGeohash()).isEqualTo(NEIGHBOUR_CELL);
    }

    @Test
    void mehrdeutigeLadungBleibtOffen() {
        var other = new NearbyStation("Discounter", true, 50, 22.0, null, 2, NEIGHBOUR_CELL,
                null, null, null, null, null, null, null, List.of(), null, null, null, null);
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.of(List.of(other, SUPERMARKT)));

        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).publicCharging(true).build());

        assertThat(log.getChargingSiteId()).isNull();
        assertThat(log.getGeohash()).isEqualTo(NEIGHBOUR_CELL);
    }

    @Test
    void großerImportFragtDasRegisterHöchstensBisZumLimit() {
        int limit = ChargingSiteAssignment.REGISTER_LOOKUPS_PER_BATCH;
        List<ChargingEntry> entries = IntStream.rangeClosed(0, limit)
                .mapToObj(i -> acCharge(LocalDateTime.of(2026, 9, 1, 8, 0).plusDays(i), NEIGHBOUR_CELL)
                        .publicCharging(true).build())
                .toList();

        var result = gateway.ingestCharging(new IngestCommand(user.getId(), car.getId(), DataSource.TESLA_LIVE,
                IngestDoor.CONNECTOR_PUSH, entries));

        List<EvLog> logs = result.created().stream()
                .map(l -> evLogRepository.findById(l.getId()).orElseThrow())
                .toList();
        assertThat(logs).hasSize(limit + 1);
        assertThat(logs.subList(0, limit)).allSatisfy(l -> assertThat(l.getChargingSiteId()).isNotNull());
        assertThat(logs.get(limit).getChargingSiteId()).isNull();
    }

    // ── Gezieltes Update: parallele Hintergrund-Schreiber und vorhandene Werte bleiben stehen ──

    @Test
    void zuordnungÜberschreibtEineParallelNachgetrageneTemperaturNicht() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).publicCharging(true).build());
        // Die Temperatur kommt an, nachdem die Zuordnung die Ladung gelesen hat
        evLogRepository.updateTemperature(log.getId(), 12.5, TemperatureSource.FORECAST);

        boolean attached = evLogRepository.attachChargingSite(attachment(log, new BigDecimal("5.80"), new BigDecimal("0.29")));

        EvLog reloaded = evLogRepository.findById(log.getId()).orElseThrow();
        assertThat(attached).isTrue();
        assertThat(reloaded.getTemperatureCelsius()).isEqualTo(12.5);
        assertThat(reloaded.getChargingSiteId()).isNotNull();
        assertThat(reloaded.getGeohash()).isEqualTo(SITE_CELL);
        assertThat(reloaded.getCostEur()).isEqualByComparingTo("5.80");
    }

    @Test
    void zuordnungLässtVorhandeneKostenUndBetreiberStehen() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).publicCharging(true)
                .cpoName("Stadtwerke").costEur(new BigDecimal("9.99")).build());

        evLogRepository.attachChargingSite(attachment(log, new BigDecimal("5.80"), new BigDecimal("0.29")));

        EvLog reloaded = evLogRepository.findById(log.getId()).orElseThrow();
        assertThat(reloaded.getCostEur()).isEqualByComparingTo("9.99");
        assertThat(reloaded.getPricePerKwh()).isNull();
        assertThat(reloaded.getCpoName()).isEqualTo("Stadtwerke");
    }

    @Test
    void zuordnungGreiftNichtMehrWennDieLadungInzwischenPrivatIst() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).publicCharging(false).build());

        boolean attached = evLogRepository.attachChargingSite(attachment(log, null, null));

        assertThat(attached).isFalse();
        assertThat(evLogRepository.findById(log.getId()).orElseThrow().getChargingSiteId()).isNull();
    }

    @Test
    void eigentümerKommtVomAutoDerLadung() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).build());

        assertThat(evLogRepository.findOwnerId(log.getId())).contains(user.getId());
        assertThat(evLogRepository.findOwnerId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void zuordnungGreiftNichtMehrWennDerNutzerDenOrtGeändertHat() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).publicCharging(true).build());

        boolean attached = evLogRepository.attachChargingSite(new ChargingSiteAttachment(log.getId(), "u0v9zqh",
                siteId(), SITE_CELL, "Supermarkt", null, null, null));

        EvLog reloaded = evLogRepository.findById(log.getId()).orElseThrow();
        assertThat(attached).isFalse();
        assertThat(reloaded.getGeohash()).isEqualTo(NEIGHBOUR_CELL);
        assertThat(reloaded.getChargingSiteId()).isNull();
    }

    @Test
    void preisDesNutzersOhneKostenBekommtKeineKostenAusDerHistorie() {
        when(nearbyCpoService.findNearbyStations(anyString())).thenReturn(Optional.empty());
        EvLog log = ingest(acCharge(LocalDateTime.of(2026, 10, 30, 20, 0), NEIGHBOUR_CELL).publicCharging(true)
                .pricePerKwh(new BigDecimal("0.49")).build());

        evLogRepository.attachChargingSite(attachment(log, new BigDecimal("5.80"), new BigDecimal("0.29")));

        EvLog reloaded = evLogRepository.findById(log.getId()).orElseThrow();
        assertThat(reloaded.getCostEur()).isNull();
        assertThat(reloaded.getPricePerKwh()).isEqualByComparingTo("0.49");
    }

    /** Säule "Supermarkt" an die Ladung, gefunden für ihren aktuellen Ort. */
    private ChargingSiteAttachment attachment(EvLog log, BigDecimal costEur, BigDecimal pricePerKwh) {
        return new ChargingSiteAttachment(log.getId(), log.getGeohash(), siteId(), SITE_CELL, "Supermarkt",
                costEur, pricePerKwh, null);
    }

    /** Eine echte Säule, damit der Fremdschlüssel auf charging_site hält. */
    private UUID siteId() {
        return chargingSiteService.siteFor(SUPERMARKT).id();
    }

    private static ChargingEntry.ChargingEntryBuilder acCharge(LocalDateTime at, String geohash) {
        return ChargingEntry.builder().loggedAt(at).geohash(geohash)
                .chargingType(ChargingType.AC).kwhCharged(new BigDecimal("20.0"));
    }

    private EvLog ingest(ChargingEntry entry) {
        return ingest(DataSource.TESLA_LIVE, entry);
    }

    private EvLog ingest(DataSource source, ChargingEntry entry) {
        var result = gateway.ingestCharging(new IngestCommand(user.getId(), car.getId(), source,
                IngestDoor.CONNECTOR_PUSH, List.of(entry)));
        assertThat(result.imported()).isEqualTo(1);
        return evLogRepository.findById(result.created().get(0).getId()).orElseThrow();
    }
}
