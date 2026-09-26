package com.evmonitor.application.spritmonitor;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.CoinLogService.CoinEvent;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.CoinLog;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.external.SpritMonitorClient;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Charakterisierung des Spritmonitor-Imports ({@link SpritMonitorImportService#importFuelings}) vor
 * der Umstellung auf das IngestGateway (Herstellerarchitektur R2g). Die Tests beschreiben das
 * heutige Verhalten, nicht das gewünschte: wer eine Regel bewusst ändert, passt den Test mit an.
 * Einfache Pfade (Geohash, Nicht-kWh, Teilfehler, Rohdaten) deckt {@code SpritMonitorImportIntegrationTest} ab.
 */
class SpritMonitorImportCharacterizationTest extends AbstractIntegrationTest {

    private static final int KWH = 5;
    private static final int VEHICLE = 42;
    private static final String TOKEN = "token";

    @MockitoBean
    private SpritMonitorClient client;

    @Autowired
    private SpritMonitorImportService importService;

    @Autowired
    private ImportEventRepository importEventRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("sm-char-" + System.nanoTime() + "@example.com");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    // --- Dedup ---------------------------------------------------------------------------------

    /** Die Dedup gilt je Quelle: ein manueller Eintrag zur selben Minute blockiert den Import nicht. */
    @Test
    void dedupIsPerSource_userLogAtSameMinute_doesNotBlockImport() {
        evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("30"), BigDecimal.ZERO, 0,
                null, null, null, null, at("15.01.2024", 0), DataSource.USER_LOGGED, ChargingType.AC, null));

        ImportResult result = importNow(fueling("15.01.2024", "40", 1000));

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(logs()).extracting(EvLog::getDataSource)
                .containsExactlyInAnyOrder(DataSource.USER_LOGGED, DataSource.SPRITMONITOR_IMPORT);
    }

    /**
     * Mehrere Ladungen an einem Tag: Mitternacht plus eine Minute je Ladung, sortiert nach Kilometerstand.
     * Anders als der 10-Minuten-Bump der Import-Tür im Gateway.
     */
    @Test
    void sameDayCharges_areSpacedByOneMinute_inOdometerOrder() {
        importNow(fueling("15.01.2024", "30", 1200), fueling("15.01.2024", "10", 1000), fueling("15.01.2024", "20", 1100));

        assertThat(logs()).extracting(EvLog::getLoggedAt, EvLog::getKwhCharged).containsExactly(
                tuple(at("15.01.2024", 0), new BigDecimal("10.00")),
                tuple(at("15.01.2024", 1), new BigDecimal("20.00")),
                tuple(at("15.01.2024", 2), new BigDecimal("30.00")));
    }

    /**
     * Die Identität einer Ladung ist ihre Position am Tag, nicht ihr Inhalt. Wird in Spritmonitor
     * nachträglich eine frühere Ladung am selben Tag erfasst, rutschen alle um eine Minute: die neue
     * Ladung wird als Duplikat übersprungen, die letzte Ladung des Tages ein zweites Mal angelegt.
     */
    @Test
    void sameDayIdentityIsPositional_lateEarlierCharge_isLost_andLastChargeDuplicated() {
        importNow(fueling("15.01.2024", "10", 1000), fueling("15.01.2024", "20", 1100));

        ImportResult second = importNow(fueling("15.01.2024", "5", 900),
                fueling("15.01.2024", "10", 1000), fueling("15.01.2024", "20", 1100));

        assertThat(second.getImported()).isEqualTo(1);
        assertThat(second.getSkipped()).isEqualTo(2);
        assertThat(logs()).extracting(EvLog::getLoggedAt, EvLog::getKwhCharged, EvLog::getOdometerKm).containsExactly(
                tuple(at("15.01.2024", 0), new BigDecimal("10.00"), 1000),
                tuple(at("15.01.2024", 1), new BigDecimal("20.00"), 1100),
                tuple(at("15.01.2024", 2), new BigDecimal("20.00"), 1100));
    }

    // --- Gelöschte Einträge ----------------------------------------------------------------------

    /** Ein gelöschter Import (Tombstone) blockiert den Re-Import; er zählt als gewöhnliches Überspringen. */
    @Test
    void deletedImport_tombstoneBlocksReimport_countsAsPlainSkip() {
        importNow(fueling("15.01.2024", "40", 1000));
        evLogRepository.softDelete(logs().get(0).getId());

        ImportResult second = importNow(fueling("15.01.2024", "40", 1000));

        assertThat(second.getImported()).isZero();
        assertThat(second.getSkipped()).isEqualTo(1);
        assertThat(second.getErrors()).isEmpty();
        assertThat(logs()).isEmpty();
    }

    /** "Alle Importe löschen" entfernt auch Tombstones hart; danach wird wieder angelegt. */
    @Test
    void deleteAllImports_removesTombstones_reimportCreatesAgain() {
        importNow(fueling("15.01.2024", "40", 1000));
        evLogRepository.softDelete(logs().get(0).getId());
        importService.deleteAllImports(user.getId());

        ImportResult second = importNow(fueling("15.01.2024", "40", 1000));

        assertThat(second.getImported()).isEqualTo(1);
        assertThat(logs()).hasSize(1);
    }

    // --- Watt ------------------------------------------------------------------------------------

    /** Je neuer Ladung SPRITMONITOR_LOG (2), beim ersten Import mit Treffern einmalig SPRITMONITOR_CONNECTED (50). */
    @Test
    void coins_twoPerNewLog_plusFiftyOnce_nothingForSkipped() {
        ImportResult first = importNow(fueling("15.01.2024", "40", 1000), fueling("16.01.2024", "30", 1200));
        ImportResult duplicate = importNow(fueling("15.01.2024", "40", 1000), fueling("16.01.2024", "30", 1200));
        ImportResult later = importNow(fueling("15.01.2024", "40", 1000), fueling("16.01.2024", "30", 1200),
                fueling("17.01.2024", "20", 1400));

        assertThat(first.getCoinsAwarded()).isEqualTo(54);
        assertThat(duplicate.getCoinsAwarded()).isZero();
        assertThat(later.getCoinsAwarded()).isEqualTo(2);
        assertThat(coins(CoinEvent.SPRITMONITOR_LOG)).containsExactly(2, 2, 2);
        assertThat(coins(CoinEvent.SPRITMONITOR_CONNECTED)).containsExactly(50);
        assertThat(coins(CoinEvent.API_UPLOAD_LOG)).isEmpty();
    }

    // --- Vererbung und Preis -------------------------------------------------------------------

    /** Reifen und Strecke werden nicht vom letzten Eintrag davor übernommen. */
    @Test
    void tireAndRouteType_areNotInheritedFromPriorLog() {
        evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("30"), BigDecimal.ZERO, 0,
                        null, null, null, null, at("10.01.2024", 0), DataSource.USER_LOGGED, ChargingType.AC, null)
                .toBuilder().tireType(TireType.WINTER).routeType(RouteType.HIGHWAY).build());

        importNow(fueling("15.01.2024", "40", 1000));

        EvLog imported = importedLog();
        assertThat(imported.getTireType()).isNull();
        assertThat(imported.getRouteType()).isNull();
    }

    /**
     * Kein Location-Pricing: fehlende Kosten werden als 0 gespeichert (nicht NULL), und die Ladekarte
     * eines früheren Eintrags am selben Ort wird nicht übernommen.
     */
    @Test
    void noLocationPricing_missingCostIsZero_cardAtSameGeohashNotAttached() {
        BigDecimal lat = new BigDecimal("52.5200");
        BigDecimal lon = new BigDecimal("13.4050");
        String geohash = GeoHash.withCharacterPrecision(lat.doubleValue(), lon.doubleValue(), 6).toBase32();
        evLogRepository.save(EvLog.createNewWithSource(car.getId(), new BigDecimal("30"), new BigDecimal("9.00"), 60,
                        geohash, null, null, null, at("10.01.2024", 0), DataSource.USER_LOGGED, ChargingType.AC, null)
                .toBuilder().chargingProviderId(UUID.randomUUID()).pricePerKwh(new BigDecimal("0.30")).build());

        importNow(new SpritMonitorFuelingDTO("15.01.2024", new BigDecimal("40"), KWH, new BigDecimal("1000"),
                null, null, null, null, new SpritMonitorFuelingDTO.Position(lat, lon), null, null, "AC", null));

        EvLog imported = importedLog();
        assertThat(imported.getGeohash()).isEqualTo(geohash);
        assertThat(imported.getCostEur()).isEqualByComparingTo("0");
        assertThat(imported.getPricePerKwh()).isNull();
        assertThat(imported.getChargingProviderId()).isNull();
    }

    // --- Protokoll und Ownership ---------------------------------------------------------------

    /** Der Spritmonitor-Import schreibt kein Import-Protokoll. */
    @Test
    void writesNoImportEvent() {
        importNow(fueling("15.01.2024", "40", 1000));

        assertThat(importEventRepository.findAll()).noneMatch(e -> user.getId().equals(e.getUserId()));
    }

    /** Fremdes oder unbekanntes Auto: IllegalArgumentException vor dem Abruf, der Controller antwortet 500. */
    @Test
    void foreignOrUnknownCar_isRejectedBeforeFetch_controllerAnswers500() {
        User other = createAndSaveUser("sm-char-other-" + System.nanoTime() + "@example.com");

        assertThatThrownBy(() -> importService.importFuelings(other.getId(), TOKEN, VEHICLE, 1, car.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User does not own the specified car");
        assertThatThrownBy(() -> importService.importFuelings(user.getId(), TOKEN, VEHICLE, 1, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Car not found");
        verify(client, never()).getFuelings(anyString(), anyInt(), anyInt());

        var response = restTemplate.exchange("/api/import/sprit-monitor/fuelings", HttpMethod.POST,
                createAuthRequest(Map.of("token", TOKEN, "vehicleId", VEHICLE, "carId", car.getId().toString()),
                        other.getId(), other.getEmail()),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(logs()).isEmpty();
    }

    // --- Helfer ----------------------------------------------------------------------------------

    private ImportResult importNow(SpritMonitorFuelingDTO... fuelings) {
        List<RawFueling> raw = Arrays.stream(fuelings).map(f -> new RawFueling(f, "{}")).toList();
        when(client.getFuelings(any(), any(), any())).thenReturn(raw);
        return importService.importFuelings(user.getId(), TOKEN, VEHICLE, 1, car.getId());
    }

    private static SpritMonitorFuelingDTO fueling(String date, String kwh, int odometer) {
        return new SpritMonitorFuelingDTO(date, new BigDecimal(kwh), KWH, new BigDecimal(odometer),
                new BigDecimal("10.00"), 60, null, null, null, null, null, "AC", null);
    }

    private static LocalDateTime at(String date, int minute) {
        return LocalDate.parse(date, SpritMonitorImportService.DD_MM_YYYY).atStartOfDay().plusMinutes(minute);
    }

    private List<EvLog> logs() {
        return evLogRepository.findAllByCarId(car.getId()).stream()
                .sorted(Comparator.comparing(EvLog::getLoggedAt).thenComparing(EvLog::getDataSource))
                .toList();
    }

    private EvLog importedLog() {
        return logs().stream().filter(l -> l.getDataSource() == DataSource.SPRITMONITOR_IMPORT)
                .reduce((a, b) -> { throw new AssertionError("mehr als ein Import"); })
                .orElseThrow();
    }

    private List<Integer> coins(CoinEvent event) {
        return coinLogRepository.findAllByUserId(user.getId()).stream()
                .filter(c -> event.getDescription().equals(c.getActionDescription()))
                .map(CoinLog::getAmount)
                .toList();
    }
}
