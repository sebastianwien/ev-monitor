package com.evmonitor.application.publicapi;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.RouteType;
import com.evmonitor.domain.TireType;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Charakterisierung von Tür 1 ({@link PublicApiImportService#importSessions}) vor dem Umbau auf
 * ein gemeinsames IngestGateway (Herstellerarchitektur R2). Die Tests beschreiben das heutige
 * Verhalten, nicht das gewünschte: wer eine Regel bewusst ändert, passt den Test mit an.
 */
class PublicApiImportCharacterizationTest extends AbstractIntegrationTest {

    @Autowired
    private PublicApiImportService importService;

    @Test
    void unsortedBatch_isImportedChronologically_nullDateCountsAsError() {
        var car = carOf("ch-sort");

        ImportApiResult result = importBatch(car, DataSource.API_UPLOAD,
                entry("2026-09-12T08:00:00Z"), entry(null), entry("2026-09-10T08:00:00Z"), entry("2026-09-11T08:00:00Z"));

        assertThat(result.imported()).isEqualTo(3);
        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.results()).extracting(ImportApiResult.ImportedSession::date)
                .containsExactly("2026-09-10T08:00:00", "2026-09-11T08:00:00", "2026-09-12T08:00:00");
    }

    @Test
    void sameTimestampInBatch_isBumpedByTenMinutes() {
        var car = carOf("ch-bump");

        importBatch(car, DataSource.API_UPLOAD, entry("2026-09-10T10:00:00Z"), entry("2026-09-10T10:00:00Z"));

        assertThat(loggedAts(car)).containsExactly(at("10:00"), at("10:10"));
    }

    /** Sortiert wird vor dem Bump: der gebumpte Eintrag verdrängt den echten 10:10-Eintrag auf 10:20. */
    @Test
    void bumpRunsAfterSorting_andCascadesIntoFollowingTimestamps() {
        var car = carOf("ch-cascade");

        importBatch(car, DataSource.API_UPLOAD,
                entry("2026-09-10T10:10:00Z"), entry("2026-09-10T10:00:00Z"), entry("2026-09-10T10:00:00Z"));

        assertThat(loggedAts(car)).containsExactly(at("10:00"), at("10:10"), at("10:20"));
    }

    /** Der Bump gilt nur innerhalb eines Batches; gegen die DB greift die Dedup und überspringt. */
    @Test
    void timestampAlreadyInDb_isSkippedNotBumped() {
        var car = carOf("ch-nobump");
        importBatch(car, DataSource.API_UPLOAD, entry("2026-09-10T10:00:00Z"));

        ImportApiResult second = importBatch(car, DataSource.API_UPLOAD, entry("2026-09-10T10:00:00Z"));

        assertThat(second.imported()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(loggedAts(car)).containsExactly(at("10:00"));
    }

    /** Das 3-Minuten-Fenster gilt nur für EU_DATA_ACT_IMPORT, nicht für den Sync. */
    @Test
    void euDataActSync_hasNoToleranceWindow() {
        var car = carOf("ch-sync");
        importBatch(car, DataSource.EU_DATA_ACT_SYNC, entry("2026-09-10T10:00:00Z"));

        ImportApiResult second = importBatch(car, DataSource.EU_DATA_ACT_SYNC, entry("2026-09-10T10:02:00Z"));

        assertThat(second.imported()).isEqualTo(1);
        assertThat(evLogRepository.findAllByCarId(car.carId())).hasSize(2);
    }

    /**
     * Dieselbe Ladung aus VW-Upload und VW-Sync wird heute zweimal angelegt, weil die Dedup je
     * dataSource prüft. Ausgangslage für D10 (Dedup je Hersteller statt je Quelle).
     */
    @Test
    void sameSessionFromUploadAndSync_isStoredTwice() {
        var car = carOf("ch-double");
        importBatch(car, DataSource.EU_DATA_ACT_IMPORT, entry("2026-09-10T10:00:00Z"));

        ImportApiResult sync = importBatch(car, DataSource.EU_DATA_ACT_SYNC, entry("2026-09-10T10:00:00Z"));

        assertThat(sync.imported()).isEqualTo(1);
        assertThat(evLogRepository.findAllByCarId(car.carId())).extracting(EvLog::getDataSource)
                .containsExactlyInAnyOrder(DataSource.EU_DATA_ACT_IMPORT, DataSource.EU_DATA_ACT_SYNC);
    }

    /** Anders als Tür 2 erbt Tür 1 Reifen- und Streckentyp bewusst nicht (Kommentar im Service). */
    @Test
    void tireAndRouteType_areNotInheritedFromPriorLog() {
        var car = carOf("ch-inherit");
        evLogRepository.save(EvLog.createNew(car.carId(), new BigDecimal("40.0"), null, 30, null, null, null,
                new BigDecimal("80"), LocalDateTime.of(2026, 9, 1, 8, 0), ChargingType.AC,
                RouteType.HIGHWAY, TireType.WINTER, false, null));

        importBatch(car, DataSource.API_UPLOAD, entry("2026-09-10T10:00:00Z"));

        EvLog imported = evLogRepository.findAllByCarId(car.carId()).stream()
                .filter(l -> l.getDataSource() == DataSource.API_UPLOAD).findFirst().orElseThrow();
        assertThat(imported.getTireType()).isNull();
        assertThat(imported.getRouteType()).isNull();
    }

    private record CarRef(UUID userId, UUID carId) {}

    private CarRef carOf(String prefix) {
        var user = createAndSaveUser(prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        var car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        return new CarRef(user.getId(), car.getId());
    }

    private static PublicApiSessionRequest.SessionEntry entry(String date) {
        return new PublicApiSessionRequest.SessionEntry(
                date, 20.0, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null, null, null);
    }

    private ImportApiResult importBatch(CarRef car, DataSource source, PublicApiSessionRequest.SessionEntry... entries) {
        return importService.importSessions(car.userId(), new PublicApiSessionRequest(car.carId(), List.of(entries)), source);
    }

    private List<LocalDateTime> loggedAts(CarRef car) {
        return evLogRepository.findAllByCarId(car.carId()).stream()
                .map(EvLog::getLoggedAt).sorted(Comparator.naturalOrder()).toList();
    }

    private static LocalDateTime at(String hhmm) {
        return LocalDateTime.parse("2026-09-10T" + hhmm);
    }
}
