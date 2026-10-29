package com.evmonitor.application.tariff.crawl;

import com.evmonitor.application.AdminAlertService;
import com.evmonitor.application.JobLockService;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nightly run under a lock (Blue/Green runs two instances), off by switch. One mail per run,
 * only if the admin has something to decide or a source failed twice in a row.
 */
@ExtendWith(MockitoExtension.class)
class TariffCrawlSchedulerTest {

    @Mock TariffCrawlService service;
    @Mock JobLockService locks;
    @Mock AdminAlertService alerts;

    private final TariffCrawlProperties props = new TariffCrawlProperties();
    private TariffCrawlScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TariffCrawlScheduler(service, locks, alerts, props, Runnable::run);
    }

    @Test
    void switchedOff_doesNothing() {
        props.setEnabled(false);

        scheduler.runWeekly();

        verify(locks, never()).tryAcquire(any(), any());
    }

    @Test
    void lockHeldByTheOtherInstance_skips() {
        when(locks.tryAcquire(eq(TariffCrawlScheduler.LOCK_NAME), any())).thenReturn(false);

        scheduler.runWeekly();

        verify(service, never()).run(anyBoolean(), any());
    }

    @Test
    void decisionsAndFailingSources_areMailedOnce_inPlainSentences() {
        when(locks.tryAcquire(eq(TariffCrawlScheduler.LOCK_NAME), any())).thenReturn(true);
        TariffProposalEntity change = proposal(TariffProposalEntity.Kind.CHANGE, Map.of("variant", "Go", "chargingType", "DC", "pricePerKwh", 0.68));
        change.setHoldReason("Sprung über 40 %: 0,39 auf 0,68");
        TariffSourceEntity broken = new TariffSourceEntity();
        broken.setEmpName("Mer");
        broken.setUrl("https://de.mer.eco/x");
        broken.setLastStatus("HTTP_ERROR");
        broken.setLastError("HTTP 404");
        when(service.run(eq(false), any())).thenReturn(new TariffCrawlService.RunSummary(Map.of(), 0, List.of(change), List.of(broken), List.of()));

        scheduler.runWeekly();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> lines = ArgumentCaptor.forClass(List.class);
        verify(alerts).sendTariffCrawlAlert(lines.capture());
        assertThat(lines.getValue()).containsExactly(
                "IONITY: Go DC neu 0,68 €/kWh. Sprung über 40 %: 0,39 auf 0,68",
                "Mer: Seite zweimal in Folge nicht lesbar (HTTP 404), https://de.mer.eco/x");
        verify(locks).release(TariffCrawlScheduler.LOCK_NAME);
    }

    @Test
    void languageModelErrors_areOneLine() {
        when(locks.tryAcquire(eq(TariffCrawlScheduler.LOCK_NAME), any())).thenReturn(true);
        when(service.run(eq(false), any())).thenReturn(new TariffCrawlService.RunSummary(Map.of(), 0, List.of(), List.of(), List.of("Mistral: REJECTED")));

        scheduler.runWeekly();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> lines = ArgumentCaptor.forClass(List.class);
        verify(alerts).sendTariffCrawlAlert(lines.capture());
        assertThat(lines.getValue()).containsExactly("Sprachmodell: Mistral: REJECTED. Die Seiten selbst waren lesbar, die Tarife bleiben unverändert.");
    }

    @Test
    void nothingToDecide_noMail() {
        when(locks.tryAcquire(eq(TariffCrawlScheduler.LOCK_NAME), any())).thenReturn(true);
        when(service.run(eq(false), any())).thenReturn(new TariffCrawlService.RunSummary(Map.of(), 2, List.of(), List.of(), List.of()));

        scheduler.runWeekly();

        verify(alerts, never()).sendTariffCrawlAlert(any());
    }

    @Test
    void manualStart_runsAllSources_unlessARunIsActive() {
        when(locks.tryAcquire(eq(TariffCrawlScheduler.LOCK_NAME), any())).thenReturn(true, false);
        when(service.run(eq(false), any())).thenReturn(new TariffCrawlService.RunSummary(Map.of(), 0, List.of(), List.of(), List.of()));

        assertThat(scheduler.tryStartManual()).isTrue();
        assertThat(scheduler.tryStartManual()).isFalse();
        verify(service).run(eq(false), any());
    }

    @Test
    void runsOnceAWeek_sundayNight() throws Exception {
        Scheduled scheduled = TariffCrawlScheduler.class.getMethod("runWeekly").getAnnotation(Scheduled.class);
        ZoneId berlin = ZoneId.of(scheduled.zone());
        CronExpression cron = CronExpression.parse(scheduled.cron());

        ZonedDateTime first = cron.next(ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, berlin));
        ZonedDateTime second = cron.next(first);

        assertThat(berlin).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(first).isEqualTo(ZonedDateTime.of(2026, 11, 8, 23, 40, 0, 0, berlin));
        assertThat(second).isEqualTo(first.plusWeeks(1));
    }

    private static TariffProposalEntity proposal(TariffProposalEntity.Kind kind, Map<String, Object> proposed) {
        TariffProposalEntity p = new TariffProposalEntity();
        p.setEmpName("IONITY");
        p.setKind(kind);
        p.setProposed(new LinkedHashMap<>(proposed));
        return p;
    }
}
