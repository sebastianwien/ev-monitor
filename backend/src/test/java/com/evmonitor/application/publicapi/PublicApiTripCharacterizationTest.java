package com.evmonitor.application.publicapi;

import com.evmonitor.application.manualimport.ManualTripImportService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Charakterisierung der Fahrten-Uploads ({@link PublicApiTripService#createTrip}, {@link PublicApiTripService#createTrips}
 * und {@link ManualTripImportService#importData}) vor der Umstellung auf das IngestGateway (Herstellerarchitektur R2g).
 * Die Tests beschreiben das heutige Verhalten, nicht das gewünschte: wer eine Regel bewusst ändert, passt den Test mit an.
 * HTTP-Status und einfache Validierungen decken {@code PublicApiTripIntegrationTest} und
 * {@code ManualTripImportControllerIntegrationTest} ab; Dedup auf echtem timestamptz {@code PublicApiTripDedupPostgresIT}.
 */
class PublicApiTripCharacterizationTest extends AbstractIntegrationTest {

    private static final String START = "2025-06-01T08:00:00Z";
    private static final String END = "2025-06-01T09:00:00Z";
    private static final String CSV_HEADER =
            "started_at,ended_at,distance_km,odometer_start_km,odometer_end_km,soc_start,soc_end,route_type";

    @Autowired
    private PublicApiTripService tripService;

    @Autowired
    private ManualTripImportService manualImport;

    @Autowired
    private EvTripRepository tripRepository;

    @Autowired
    private ImportEventRepository importEventRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("trip-char-" + System.nanoTime() + "@example.com");
        car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
    }

    // --- Dedup ---------------------------------------------------------------------------------

    /** Kein Zeitfenster: die Dedup vergleicht den Startzeitpunkt sekundengenau, eine Sekunde später ist eine neue Fahrt. */
    @Test
    void dedupIsExactStartInstant_oneSecondLater_isNewTrip() {
        ImportApiResult result = tripService.createTrips(user.getId(), car.getId(), List.of(
                trip(START, END), trip("2025-06-01T08:00:01Z", END)));

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
    }

    /** Die Dedup gilt je Auto: dieselbe Startzeit an einem anderen Auto des Nutzers blockiert nicht. */
    @Test
    void dedupIsPerCar_sameStartOnOtherCar_isImported() {
        Car second = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_Y);
        tripService.createTrip(user.getId(), trip(second.getId(), START, END));

        ImportApiResult result = tripService.createTrips(user.getId(), car.getId(), List.of(trip(START, END)));

        assertThat(result.imported()).isEqualTo(1);
    }

    /** Im selben Batch erkennt die Dedup denselben Zeitpunkt auch in einem anderen Offset. */
    @Test
    void batchDedup_sameInstantInOtherOffset_isSkipped() {
        ImportApiResult result = tripService.createTrips(user.getId(), car.getId(), List.of(
                trip(START, END), trip("2025-06-01T10:00:00+02:00", "2025-06-01T11:00:00+02:00")));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    // --- Gelöschte Fahrten ---------------------------------------------------------------------

    /**
     * Eine gelöschte Fahrt blockiert den Re-Import auch im Batch. Sie zählt als normales {@code skipped}:
     * anders als bei Ladungen kein {@code skippedDeleted} und kein Hinweis.
     */
    @Test
    void batch_deletedTrip_blocksReimport_countsAsPlainSkip() {
        tripService.createTrips(user.getId(), car.getId(), List.of(trip(START, END)));
        UUID id = liveTrips().get(0).getId();
        tripService.deleteTrip(user.getId(), id);

        ImportApiResult reimport = tripService.createTrips(user.getId(), car.getId(), List.of(trip(START, END)));

        assertThat(reimport.imported()).isZero();
        assertThat(reimport.skipped()).isEqualTo(1);
        assertThat(reimport.skippedDeleted()).isZero();
        assertThat(reimport.hint()).isNull();
        assertThat(liveTrips()).isEmpty();
        assertThat(tripRepository.findById(id).orElseThrow().getDeletedAt()).isNotNull();
    }

    // --- Ownership -----------------------------------------------------------------------------

    /** Fremdes Auto: {@link SecurityException} (Controller 403), auch im Batch vor dem ersten Eintrag. */
    @Test
    void foreignCar_isSecurityException_nothingSaved() {
        User stranger = createAndSaveUser("trip-char-stranger-" + System.nanoTime() + "@example.com");

        assertThatThrownBy(() -> tripService.createTrip(stranger.getId(), trip(START, END)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> tripService.createTrips(stranger.getId(), car.getId(), List.of(trip(START, END))))
                .isInstanceOf(SecurityException.class);
        assertThat(liveTrips()).isEmpty();
    }

    /** Unbekanntes Auto und fehlende car_id: {@link IllegalArgumentException} (Controller 400), nicht 404. */
    @Test
    void unknownCarOrMissingCarId_isIllegalArgument() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> tripService.createTrip(user.getId(), trip(unknown, START, END)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Fahrzeug nicht gefunden");
        assertThatThrownBy(() -> tripService.createTrips(user.getId(), unknown, List.of(trip(START, END))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Fahrzeug nicht gefunden");
        assertThatThrownBy(() -> tripService.createTrips(user.getId(), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("car_id darf nicht leer sein");
    }

    /** Ein gelöschtes Auto (7-Tage-Fenster) gilt als unbekannt. */
    @Test
    void deletedCar_isTreatedAsUnknown() {
        carRepository.save(car.softDelete());

        assertThatThrownBy(() -> tripService.createTrips(user.getId(), car.getId(), List.of(trip(START, END))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Fahrzeug nicht gefunden");
        assertThat(tripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId())).isEmpty();
    }

    /** Manueller Import: auch eine Datei aus lauter ungültigen Zeilen prüft den Besitz. */
    @Test
    void manualImport_allRowsInvalid_stillChecksOwnership() {
        User stranger = createAndSaveUser("trip-char-stranger-" + System.nanoTime() + "@example.com");
        String csv = CSV_HEADER + "\n," + END + ",10,,,,,";

        assertThatThrownBy(() -> manualImport.importData(stranger.getId(), car.getId(), "csv", csv))
                .isInstanceOf(SecurityException.class);
    }

    /** Manueller Import: eine nicht lesbare Datei endet vor der Besitzprüfung mit einem Fehler, ohne Exception. */
    @Test
    void manualImport_unreadableFile_returnsErrorBeforeOwnershipCheck() {
        User stranger = createAndSaveUser("trip-char-stranger-" + System.nanoTime() + "@example.com");

        ImportApiResult result = manualImport.importData(stranger.getId(), car.getId(), "json", "kein json");

        assertThat(result.imported()).isZero();
        assertThat(result.errors()).isEqualTo(1);
    }

    // --- Fehlerbild ----------------------------------------------------------------------------

    /** Batch: ein ungültiger Eintrag zählt als Fehler, die gültigen werden trotzdem angelegt. */
    @Test
    void batch_invalidEntriesAreIsolated_validOnesImported() {
        ImportApiResult result = tripService.createTrips(user.getId(), car.getId(), List.of(
                trip(START, END),
                trip("2025-06-02T09:00:00Z", "2025-06-02T08:00:00Z"),
                trip("2025-06-03T08:00:00", "2025-06-03T09:00:00"),
                new PublicApiTripRequest(car.getId(), "2025-06-04T08:00:00Z", "2025-06-04T09:00:00Z",
                        new BigDecimal("10"), null, null, null, null, "OFFROAD")));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.errors()).isEqualTo(3);
        assertThat(result.skipped()).isZero();
    }

    /** Einzel-Upload: ein ungültiger Eintrag ist eine {@link IllegalArgumentException} mit Klartext (Controller 400). */
    @Test
    void single_invalidEntry_isIllegalArgumentWithMessage_nothingSaved() {
        assertThatThrownBy(() -> tripService.createTrip(user.getId(), trip("2025-06-01T08:00:00", END)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ungültiges Datumsformat für started_at. Erwartet: ISO 8601 mit Timezone-Offset");
        assertThat(liveTrips()).isEmpty();
    }

    /** Doppelte Startzeit beim Einzel-Upload: {@link IllegalArgumentException} (Controller 400), keine Antwort mit id. */
    @Test
    void single_duplicate_isIllegalArgument() {
        tripService.createTrip(user.getId(), trip(START, END));

        assertThatThrownBy(() -> tripService.createTrip(user.getId(), trip(START, END)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Für dieses Fahrzeug existiert bereits eine Fahrt mit diesem Startzeitpunkt");
    }

    /** Manueller Import: eine zu kurze Zeile zählt als Warnung und wird trotzdem angelegt. */
    @Test
    void manualImport_shortRow_countsAsWarning_andIsImported() {
        String csv = CSV_HEADER + "\n" + START + "," + END + ",12.5";

        ImportApiResult result = manualImport.importData(user.getId(), car.getId(), "csv", csv);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.warnings()).isEqualTo(1);
        assertThat(result.errors()).isZero();
    }

    // --- Watt, Protokoll, Vererbung ------------------------------------------------------------

    /** Keine Watt, auch nicht für den Einzel-Upload (Batch: {@code ManualTripImportControllerIntegrationTest}). */
    @Test
    void single_awardsNoCoins() {
        tripService.createTrip(user.getId(), trip(START, END));

        assertThat(coinLogRepository.findAllByUserId(user.getId())).isEmpty();
    }

    /** Heute kein Import-Protokoll, weder Einzel-Upload noch Batch noch manueller Import. */
    @Test
    void writesNoImportEvent() {
        tripService.createTrip(user.getId(), trip(START, END));
        tripService.createTrips(user.getId(), car.getId(), List.of(trip("2025-06-02T08:00:00Z", "2025-06-02T09:00:00Z")));
        manualImport.importData(user.getId(), car.getId(), "csv",
                CSV_HEADER + "\n2025-06-03T08:00:00Z,2025-06-03T09:00:00Z,10,,,,,");

        assertThat(liveTrips()).hasSize(3);
        assertThat(importEventRepository.findAll()).filteredOn(e -> user.getId().equals(e.getUserId())).isEmpty();
    }

    /** Die Strecke kommt nur aus dem Eintrag, nichts wird von der Fahrt davor übernommen. */
    @Test
    void routeTypeIsNotInheritedFromPriorTrip() {
        tripService.createTrip(user.getId(), new PublicApiTripRequest(car.getId(), START, END,
                new BigDecimal("10"), null, null, null, null, "CITY"));

        ApiTripResponse next = tripService.createTrip(user.getId(), trip("2025-06-02T08:00:00Z", "2025-06-02T09:00:00Z"));

        assertThat(tripRepository.findById(next.id()).orElseThrow().getRouteType()).isNull();
    }

    // --- Mapping -------------------------------------------------------------------------------

    /** API und CSV landen gleich: Quelle API_UPLOAD, als vom Nutzer angelegt markiert, ohne externalId. */
    @Test
    void mapping_apiAndCsv_bothApiUpload_userCreated_withoutExternalId() {
        tripService.createTrip(user.getId(), trip(START, END));
        manualImport.importData(user.getId(), car.getId(), "csv",
                CSV_HEADER + "\n2025-06-02T08:00:00Z,2025-06-02T09:00:00Z,10,,,,,");

        assertThat(liveTrips()).hasSize(2).allSatisfy(t -> {
            assertThat(t.getDataSource()).isEqualTo(EvTrip.DATA_SOURCE_API_UPLOAD);
            assertThat(t.isUserCreated()).isTrue();
            assertThat(t.getExternalId()).isNull();
            assertThat(t.getStatus()).isEqualTo("COMPLETED");
            assertThat(t.getUserEditedAt()).isNull();
        });
    }

    /** Angegebene Strecke schlägt die Differenz der Kilometerstände, auch wenn beide nicht zusammenpassen. */
    @Test
    void distanceGiven_winsOverOdometerDelta() {
        ApiTripResponse saved = tripService.createTrip(user.getId(), new PublicApiTripRequest(car.getId(), START, END,
                new BigDecimal("30"), new BigDecimal("1000"), new BigDecimal("1025"), null, null, null));

        assertThat(tripRepository.findById(saved.id()).orElseThrow().getDistanceKm()).isEqualByComparingTo("30");
    }

    /** Verbrauch aus dem SoC-Rückgang mit SoH-bereinigter Kapazität (75 kWh, 20 % Degradation = 60 kWh). */
    @Test
    void estimatedConsumedKwh_fromSocDrop_withSohAdjustedCapacity_nullWhenSocRises() {
        carRepository.save(car.toBuilder().batteryDegradationPercent(new BigDecimal("20")).build());

        ApiTripResponse drop = tripService.createTrip(user.getId(), new PublicApiTripRequest(car.getId(), START, END,
                new BigDecimal("50"), null, null, new BigDecimal("80"), new BigDecimal("60"), null));
        ApiTripResponse rise = tripService.createTrip(user.getId(), new PublicApiTripRequest(car.getId(),
                "2025-06-02T08:00:00Z", "2025-06-02T09:00:00Z",
                new BigDecimal("5"), null, null, new BigDecimal("60"), new BigDecimal("62"), null));

        assertThat(tripRepository.findById(drop.id()).orElseThrow().getEstimatedConsumedKwh()).isEqualByComparingTo("12.00");
        assertThat(tripRepository.findById(rise.id()).orElseThrow().getEstimatedConsumedKwh()).isNull();
    }

    private PublicApiTripRequest trip(String startedAt, String endedAt) {
        return trip(car.getId(), startedAt, endedAt);
    }

    private static PublicApiTripRequest trip(UUID carId, String startedAt, String endedAt) {
        return new PublicApiTripRequest(carId, startedAt, endedAt, new BigDecimal("10"), null, null, null, null, null);
    }

    private List<EvTrip> liveTrips() {
        return tripRepository.findAllByCarIdAndDeletedAtIsNull(car.getId());
    }
}
