package com.evmonitor.application.publicapi;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Derselbe Ladevorgang kommt aus dem VW-Portal zweimal: einmal im 15-Minuten-Feed, einmal im
 * Historien-Export - mit leicht abweichendem Startzeitpunkt. Fuer EU-Data-Act-Importe gilt
 * deshalb ein Toleranzfenster von 3 Minuten um den Startzeitpunkt; alle anderen Quellen
 * bleiben minutengenau.
 */
class PublicApiImportDedupWindowTest extends AbstractIntegrationTest {

    @Autowired
    private PublicApiImportService importService;

    @Test
    void euDataAct_sessionWithinThreeMinutes_isSkippedAsDuplicate() {
        var car = carOf("dd-near");
        importOne(car, "2026-09-10T10:00:00Z", DataSource.EU_DATA_ACT_IMPORT);

        ImportApiResult second = importOne(car, "2026-09-10T10:02:30Z", DataSource.EU_DATA_ACT_IMPORT);

        assertEquals(0, second.imported());
        assertEquals(1, second.skipped());
    }

    @Test
    void euDataAct_sessionBeforeExistingWithinWindow_isSkippedToo() {
        var car = carOf("dd-before");
        importOne(car, "2026-09-10T10:00:00Z", DataSource.EU_DATA_ACT_IMPORT);

        ImportApiResult second = importOne(car, "2026-09-10T09:57:30Z", DataSource.EU_DATA_ACT_IMPORT);

        assertEquals(1, second.skipped());
    }

    @Test
    void euDataAct_sessionOutsideThreeMinutes_isImported() {
        var car = carOf("dd-far");
        importOne(car, "2026-09-10T10:00:00Z", DataSource.EU_DATA_ACT_IMPORT);

        ImportApiResult second = importOne(car, "2026-09-10T10:04:00Z", DataSource.EU_DATA_ACT_IMPORT);

        assertEquals(1, second.imported());
        assertEquals(0, second.skipped());
    }

    @Test
    void otherSources_stayMinuteExact() {
        var car = carOf("dd-api");
        importOne(car, "2026-09-10T10:00:00Z", DataSource.API_UPLOAD);

        ImportApiResult second = importOne(car, "2026-09-10T10:02:00Z", DataSource.API_UPLOAD);

        assertEquals(1, second.imported());
        assertEquals(0, second.skipped());
    }

    @Test
    void euDataAct_windowDoesNotMatchOtherSources() {
        var car = carOf("dd-mix");
        importOne(car, "2026-09-10T10:00:00Z", DataSource.API_UPLOAD);

        ImportApiResult second = importOne(car, "2026-09-10T10:01:00Z", DataSource.EU_DATA_ACT_IMPORT);

        assertEquals(1, second.imported());
    }

    /**
     * Der User hat den Vorgang gelöscht. Der nächste AutoSync liefert ihn wieder und darf ihn
     * nicht neu anlegen, sonst wäre Löschen wirkungslos.
     */
    @Test
    void softDeletedSession_isStillTreatedAsDuplicate() {
        var car = carOf("dd-deleted");
        importOne(car, "2026-09-10T10:00:00Z", DataSource.EU_DATA_ACT_IMPORT);
        var logId = evLogRepository.findAllByCarId(car.carId()).get(0).getId();
        evLogRepository.softDelete(logId);

        ImportApiResult again = importOne(car, "2026-09-10T10:00:00Z", DataSource.EU_DATA_ACT_IMPORT);

        assertEquals(0, again.imported());
        assertEquals(1, again.skipped());
        assertEquals(0, evLogRepository.findAllByCarId(car.carId()).size());
    }

    private record CarRef(UUID userId, UUID carId) {}

    private CarRef carOf(String prefix) {
        var user = createAndSaveUser(prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de");
        var car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        return new CarRef(user.getId(), car.getId());
    }

    private ImportApiResult importOne(CarRef car, String date, DataSource source) {
        var entry = new PublicApiSessionRequest.SessionEntry(
                date, 20.0, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null, null, null);
        return importService.importSessions(car.userId(), new PublicApiSessionRequest(car.carId(), List.of(entry)), source);
    }
}
