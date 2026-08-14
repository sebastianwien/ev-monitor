package com.evmonitor.infrastructure.weather;

import ch.hsr.geohash.GeoHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Der Runner traegt die Teile, die beide Backfills (Logs und Fahrten) gleich brauchen: ein
 * Aufruf-Budget, Bündelung mehrerer Zeilen auf einen Wetterabruf, und einen Abbruch, der einen
 * Ausfall der Gegenstelle nicht in stundenlanges Weiterhaemmern uebersetzt.
 */
class TemperatureBackfillRunnerTest {

    private static java.util.TimeZone originalTimeZone;

    @org.junit.jupiter.api.BeforeAll
    static void fixTimeZone() {
        originalTimeZone = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"));
    }

    @org.junit.jupiter.api.AfterAll
    static void restoreTimeZone() {
        java.util.TimeZone.setDefault(originalTimeZone);
    }

    private static final String BERLIN = GeoHash.geoHashStringWithCharacterPrecision(52.53, 13.46, 7);
    private static final String WIEN = GeoHash.geoHashStringWithCharacterPrecision(48.20, 16.37, 7);

    private TemperatureService temperatureService;
    private TemperatureBackfillRunner runner;
    private Map<UUID, Double> written;

    @BeforeEach
    void setUp() {
        temperatureService = mock(TemperatureService.class);
        runner = new TemperatureBackfillRunner(temperatureService);
        written = new LinkedHashMap<>();
    }

    private void respondWithHourEqualsValue() {
        when(temperatureService.getHourlyForUtcDay(anyDouble(), anyDouble(), any()))
                .thenReturn(IntStream.range(0, 24).mapToObj(Double::valueOf).toList());
    }

    private TemperatureBackfillRunner.Candidate log(String geohash, LocalDateTime at) {
        return new TemperatureBackfillRunner.Candidate(UUID.randomUUID(),
                List.of(new TemperatureBackfillRunner.Point(geohash, at)));
    }

    private TemperatureBackfillRunner.Summary run(List<TemperatureBackfillRunner.Candidate> candidates, int maxCalls) {
        return runner.run("test", candidates, written::put,
                new TemperatureBackfillRunner.Budget(maxCalls, 0, 5, java.time.Duration.ofMinutes(5)));
    }

    @Test
    void schreibtDenWertDerPassendenUtcStunde() {
        respondWithHourEqualsValue();
        var candidate = log(BERLIN, LocalDateTime.of(2026, 8, 14, 12, 5)); // 12:05 CEST = 10:05 UTC

        var summary = run(List.of(candidate), 100);

        assertThat(written).containsExactly(Map.entry(candidate.id(), 10.0));
        assertThat(summary.enriched()).isEqualTo(1);
    }

    @Test
    void mitteltMehrerePunkteEinerZeile() {
        respondWithHourEqualsValue();
        UUID id = UUID.randomUUID();
        var trip = new TemperatureBackfillRunner.Candidate(id, List.of(
                new TemperatureBackfillRunner.Point(BERLIN, LocalDateTime.of(2026, 8, 14, 12, 0)),   // 10 UTC
                new TemperatureBackfillRunner.Point(BERLIN, LocalDateTime.of(2026, 8, 14, 14, 0))));  // 12 UTC

        run(List.of(trip), 100);

        assertThat(written).containsExactly(Map.entry(id, 11.0));
    }

    @Test
    void holtOrtUndTagNurEinmal() {
        respondWithHourEqualsValue();
        LocalDateTime morgens = LocalDateTime.of(2026, 8, 14, 8, 0);
        LocalDateTime abends = LocalDateTime.of(2026, 8, 14, 20, 0);

        var summary = run(List.of(log(BERLIN, morgens), log(BERLIN, abends), log(WIEN, morgens)), 100);

        assertThat(summary.apiCalls()).isEqualTo(2); // Berlin einmal, Wien einmal
        assertThat(summary.enriched()).isEqualTo(3);
    }

    @Test
    void haeltDasAufrufBudgetEinUndMeldetDenGrund() {
        respondWithHourEqualsValue();
        List<TemperatureBackfillRunner.Candidate> candidates = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            candidates.add(log(BERLIN, LocalDateTime.of(2026, 8, day, 10, 0)));
        }

        var summary = run(candidates, 3);

        assertThat(summary.apiCalls()).isEqualTo(3);
        assertThat(summary.enriched()).isEqualTo(3);
        assertThat(summary.stopReason()).isEqualTo("call budget reached");
    }

    @Test
    void brichtNachWiederholtenFehlernAbStattWeiterzuhaemmern() {
        when(temperatureService.getHourlyForUtcDay(anyDouble(), anyDouble(), any()))
                .thenThrow(HttpClientErrorException.TooManyRequests.class);
        List<TemperatureBackfillRunner.Candidate> candidates = new ArrayList<>();
        for (int day = 1; day <= 20; day++) {
            candidates.add(log(BERLIN, LocalDateTime.of(2026, 8, day, 10, 0)));
        }

        var summary = run(candidates, 100);

        assertThat(summary.apiCalls()).isEqualTo(5);
        assertThat(summary.stopReason()).isEqualTo("too many consecutive errors");
        assertThat(written).isEmpty();
    }

    @Test
    void laesstZeilenOhneWertUnangetastet() {
        when(temperatureService.getHourlyForUtcDay(anyDouble(), anyDouble(), any())).thenReturn(List.of());

        var summary = run(List.of(log(BERLIN, LocalDateTime.of(2026, 8, 14, 10, 0))), 100);

        assertThat(written).isEmpty();
        assertThat(summary.enriched()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
    }

    @Test
    void fragtDenUtcTagAbNichtDenOertlichen() {
        respondWithHourEqualsValue();
        // 01:30 Ortszeit Berlin ist in UTC noch der Vortag - der Abruf muss dem folgen
        run(List.of(log(BERLIN, LocalDateTime.of(2026, 4, 21, 1, 30))), 100);

        verify(temperatureService).getHourlyForUtcDay(anyDouble(), anyDouble(), eq(LocalDate.of(2026, 4, 20)));
    }
}
