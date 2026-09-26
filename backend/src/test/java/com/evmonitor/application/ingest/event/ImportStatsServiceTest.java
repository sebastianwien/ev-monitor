package com.evmonitor.application.ingest.event;

import com.evmonitor.application.ingest.event.ImportStatsResponse.Group;
import com.evmonitor.application.ingest.event.ImportStatsResponse.Health;
import com.evmonitor.domain.DataSource;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin-Sicht auf das Import-Protokoll: Aggregate je Provider und Kanal, Ampel aus der Fehlerquote,
 * Tagesverlauf nach Ergebnis und die häufigsten Fehler. Keine Nutzer- oder Auto-Bezüge.
 */
class ImportStatsServiceTest extends AbstractIntegrationTest {

    @Autowired ImportStatsService service;
    @Autowired ImportEventRepository repository;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    @ParameterizedTest(name = "{0} Störungen von {1} -> {2}")
    @CsvSource({
            "0, 10, OK",
            "0, 0, OK",
            "1, 21, OK",
            "1, 20, WARN",
            "3, 16, WARN",
            "1, 5, ERROR",
            "4, 4, ERROR",
    })
    void healthFromFailureRate(long failures, long events, Health expected) {
        assertThat(Health.of(failures, events)).isEqualTo(expected);
    }

    @Test
    void groupsByProviderAndChannel_withCountsLastSuccessAndTopErrors() {
        // Auf Millisekunden gekürzt: Linux liefert Nanosekunden, die Spalte speichert Mikrosekunden.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        save(DataSource.EU_DATA_ACT_SYNC, ImportEventOutcome.IMPORTED, 3, 1, null, now.minusDays(2));
        save(DataSource.EU_DATA_ACT_SYNC, ImportEventOutcome.NO_NEW_DATA, 0, 2, null, now.minusDays(1));
        save(DataSource.EU_DATA_ACT_SYNC, ImportEventOutcome.PARSE_ERROR, 0, 0, "E: Format A", now.minusHours(3));
        save(DataSource.EU_DATA_ACT_SYNC, ImportEventOutcome.PARSE_ERROR, 0, 0, "E: Format A", now.minusHours(2));
        save(DataSource.EU_DATA_ACT_SYNC, ImportEventOutcome.FAILED, 0, 0, "E: B", now.minusHours(1));
        save(DataSource.EU_DATA_ACT_SYNC, ImportEventOutcome.REJECTED, 0, 0, "E: C", now.minusMinutes(50));
        save(DataSource.EU_DATA_ACT_SYNC, ImportEventOutcome.FAILED, 0, 0, "E: D", now.minusMinutes(40));
        save(DataSource.EU_DATA_ACT_SYNC, ImportEventOutcome.FAILED, 0, 0, "E: D", now.minusMinutes(30));
        save(DataSource.TESLA_LIVE, ImportEventOutcome.IMPORTED, 1, 0, null, now.minusHours(5));
        save(DataSource.TESLA_LIVE, ImportEventOutcome.IMPORTED, 1, 0, null, now.minusDays(40));

        ImportStatsResponse stats = service.stats(30);

        assertThat(stats.days()).isEqualTo(30);
        assertThat(stats.groups()).hasSize(2);
        Group vw = group(stats, "VW_GROUP", "SYNC");
        assertThat(vw.events()).isEqualTo(8);
        assertThat(vw.sessionsImported()).isEqualTo(3);
        assertThat(vw.sessionsSkipped()).isEqualTo(3);
        assertThat(vw.outcomes()).containsEntry(ImportEventOutcome.PARSE_ERROR, 2L).containsEntry(ImportEventOutcome.FAILED, 3L);
        // 5 Störungen (PARSE_ERROR + FAILED) von 8, REJECTED zählt nicht
        assertThat(vw.errorRate()).isEqualTo(0.625);
        assertThat(vw.health()).isEqualTo(Health.ERROR);
        assertThat(vw.lastSuccessAt()).isEqualTo(instant(now.minusDays(1)));
        assertThat(vw.lastEventAt()).isEqualTo(instant(now.minusMinutes(30)));
        assertThat(vw.topErrors()).extracting(ImportStatsResponse.TopError::error)
                .containsExactly("E: D", "E: Format A", "E: C");
        assertThat(vw.topErrors().get(0).count()).isEqualTo(2);

        Group tesla = group(stats, "TESLA", "LIVE");
        assertThat(tesla.events()).as("Event vor 40 Tagen liegt außerhalb").isEqualTo(1);
        assertThat(tesla.health()).isEqualTo(Health.OK);
        assertThat(tesla.topErrors()).isEmpty();
    }

    @Test
    void dailySeries_countsPerDayAndOutcome() {
        LocalDateTime today = LocalDateTime.now().withHour(12);
        save(DataSource.TESLA_LIVE, ImportEventOutcome.IMPORTED, 1, 0, null, today);
        save(DataSource.WALLBOX_GOE, ImportEventOutcome.IMPORTED, 1, 0, null, today.minusMinutes(1));
        save(DataSource.WALLBOX_GOE, ImportEventOutcome.FAILED, 0, 0, "x", today.minusDays(1));

        ImportStatsResponse stats = service.stats(7);

        assertThat(stats.daily()).containsExactly(
                new ImportStatsResponse.DailyCount(today.toLocalDate().minusDays(1), ImportEventOutcome.FAILED, 1),
                new ImportStatsResponse.DailyCount(today.toLocalDate(), ImportEventOutcome.IMPORTED, 2));
    }

    private static java.time.Instant instant(LocalDateTime serverTime) {
        return serverTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    private static Group group(ImportStatsResponse stats, String provider, String channel) {
        return stats.groups().stream()
                .filter(g -> g.provider().equals(provider) && g.channel().equals(channel))
                .findFirst().orElseThrow();
    }

    private void save(DataSource source, ImportEventOutcome outcome, int imported, int skipped, String error,
                      LocalDateTime at) {
        repository.save(ImportEvent.of(source, userId, null).outcome(outcome)
                .sessionsImported(imported).sessionsSkipped(skipped).error(error).createdAt(at).build());
    }
}
