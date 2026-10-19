package com.evmonitor.application.seo;

import com.evmonitor.application.JobLockService;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoSyncSchedulerTest {

    /** 19.10.2026 04:30 Europe/Berlin, also "heute" = 19.10. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Europe/Berlin"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private final SeoSyncService syncService = mock(SeoSyncService.class);
    private final GscDailyRepository repository = mock(GscDailyRepository.class);
    private final JobLockService locks = mock(JobLockService.class);
    private final GscProperties properties = new GscProperties();
    private final PlausibleSyncService plausibleSync = mock(PlausibleSyncService.class);
    private SeoSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties.setRetentionDays(490);
        scheduler = new SeoSyncScheduler(new SeoSyncScheduler.Deps(syncService, repository, locks, properties,
                new SyncTaskExecutor()), plausibleSync, CLOCK);
        when(syncService.isEnabled()).thenReturn(true);
        when(plausibleSync.isEnabled()).thenReturn(true);
        when(plausibleSync.hasAnyData()).thenReturn(true);
        when(plausibleSync.backfillFrom()).thenReturn(LocalDate.of(2026, 3, 1));
        when(locks.tryAcquire(eq(SeoSyncScheduler.LOCK_NAME), any())).thenReturn(true);
    }

    @Test
    void nightlyUsesRollingWindowWhenDataExists() {
        when(repository.hasAnyData()).thenReturn(true);

        scheduler.runNightly();

        verify(syncService).sync(TODAY.minusDays(8), TODAY.minusDays(2));
        verify(plausibleSync).sync(TODAY.minusDays(7), TODAY.minusDays(1));
        verify(locks).release(SeoSyncScheduler.LOCK_NAME);
    }

    @Test
    void plausibleBackfillsFromConfiguredStartAndRunsWithoutGsc() {
        when(syncService.isEnabled()).thenReturn(false);
        when(plausibleSync.hasAnyData()).thenReturn(false);

        scheduler.runNightly();

        verify(syncService, never()).sync(any(), any());
        verify(plausibleSync).sync(LocalDate.of(2026, 3, 1), TODAY.minusDays(1));
        verify(locks).release(SeoSyncScheduler.LOCK_NAME);
    }

    @Test
    void gscFailureDoesNotStopPlausible() {
        when(repository.hasAnyData()).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(syncService).sync(any(), any());

        scheduler.runNightly();

        verify(plausibleSync).sync(TODAY.minusDays(7), TODAY.minusDays(1));
        verify(locks).release(SeoSyncScheduler.LOCK_NAME);
    }

    @Test
    void nightlyBackfillsWholeRetentionOnEmptyTable() {
        when(repository.hasAnyData()).thenReturn(false);

        scheduler.runNightly();

        verify(syncService).sync(TODAY.minusDays(490), TODAY.minusDays(2));
    }

    @Test
    void nightlySkipsWithoutKeyOrWhenLockIsHeld() {
        when(syncService.isEnabled()).thenReturn(false);
        when(plausibleSync.isEnabled()).thenReturn(false);
        scheduler.runNightly();
        verify(locks, never()).tryAcquire(any(), any());

        when(syncService.isEnabled()).thenReturn(true);
        when(plausibleSync.isEnabled()).thenReturn(true);
        when(locks.tryAcquire(eq(SeoSyncScheduler.LOCK_NAME), any())).thenReturn(false);
        scheduler.runNightly();
        verify(syncService, never()).sync(any(), any());
        verify(plausibleSync, never()).sync(any(), any());
        verify(locks, never()).release(any());
    }

    @Test
    void releasesLockEvenWhenSyncThrows() {
        when(repository.hasAnyData()).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(syncService).sync(any(), any());

        scheduler.runNightly();

        InOrder order = inOrder(locks, syncService);
        order.verify(locks).tryAcquire(eq(SeoSyncScheduler.LOCK_NAME), any());
        order.verify(syncService).sync(any(), any());
        order.verify(locks).release(SeoSyncScheduler.LOCK_NAME);
    }

    @Test
    void manualStartRunsRequestedWindowAndRefusesWhenBusy() {
        assertThat(scheduler.tryStartManual(30)).isTrue();
        verify(syncService).sync(TODAY.minusDays(31), TODAY.minusDays(2));
        verify(plausibleSync).sync(TODAY.minusDays(30), TODAY.minusDays(1));
        verify(locks).release(SeoSyncScheduler.LOCK_NAME);

        when(locks.tryAcquire(eq(SeoSyncScheduler.LOCK_NAME), any())).thenReturn(false);
        assertThat(scheduler.tryStartManual(30)).isFalse();
    }

    @Test
    void manualStartCapsWindow() {
        scheduler.tryStartManual(100_000);
        verify(syncService).sync(TODAY.minusDays(2).minusDays(SeoSyncScheduler.MAX_MANUAL_DAYS - 1), TODAY.minusDays(2));
    }
}
