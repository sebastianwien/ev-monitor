package com.evmonitor.application.seo;

import com.evmonitor.application.JobLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoCompetitionSchedulerTest {

    private final SeoCompetitionService service = mock(SeoCompetitionService.class);
    private final JobLockService locks = mock(JobLockService.class);
    private SeoCompetitionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SeoCompetitionScheduler(service, locks, new SyncTaskExecutor());
        when(service.isEnabled()).thenReturn(true);
        when(locks.tryAcquire(eq(SeoCompetitionScheduler.LOCK_NAME), any())).thenReturn(true);
    }

    @Test
    void nightlyRunsUnderOwnLockAndReleasesEvenOnFailure() {
        doThrow(new IllegalStateException("kaputt")).when(service).run();

        scheduler.runNightly();

        verify(service).run();
        verify(locks).release(SeoCompetitionScheduler.LOCK_NAME);
    }

    @Test
    void skipsWithoutAccessOrWhenLocked() {
        when(service.isEnabled()).thenReturn(false);
        scheduler.runNightly();
        assertThat(scheduler.tryStartManual()).isFalse();
        verify(locks, never()).tryAcquire(any(), any());

        when(service.isEnabled()).thenReturn(true);
        when(locks.tryAcquire(eq(SeoCompetitionScheduler.LOCK_NAME), any())).thenReturn(false);
        scheduler.runNightly();
        assertThat(scheduler.tryStartManual()).isFalse();
        verify(service, never()).run();
    }

    @Test
    void manualStartRunsInBackground() {
        assertThat(scheduler.tryStartManual()).isTrue();
        verify(service).run();
        verify(locks).release(SeoCompetitionScheduler.LOCK_NAME);
    }
}
