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

class SeoAuditSchedulerTest {

    private final SeoAuditService service = mock(SeoAuditService.class);
    private final SeoVitalsService vitals = mock(SeoVitalsService.class);
    private final JobLockService locks = mock(JobLockService.class);
    private SeoAuditScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SeoAuditScheduler(service, vitals, locks, new SyncTaskExecutor());
        when(service.isEnabled()).thenReturn(true);
        when(locks.tryAcquire(eq(SeoAuditScheduler.LOCK_NAME), any())).thenReturn(true);
    }

    @Test
    void nightlyRunsUnderOwnLockAndReleases() {
        scheduler.runNightly();

        verify(service).run();
        verify(vitals).refreshIfDue();
        verify(locks).release(SeoAuditScheduler.LOCK_NAME);
    }

    @Test
    void nightlySkipsWhenDisabledOrLocked() {
        when(service.isEnabled()).thenReturn(false);
        scheduler.runNightly();
        verify(locks, never()).tryAcquire(any(), any());

        when(service.isEnabled()).thenReturn(true);
        when(locks.tryAcquire(eq(SeoAuditScheduler.LOCK_NAME), any())).thenReturn(false);
        scheduler.runNightly();
        verify(service, never()).run();
    }

    @Test
    void manualStartReportsLockAndReleasesAfterFailure() {
        doThrow(new IllegalStateException("kaputt")).when(service).run();

        assertThat(scheduler.tryStartManual()).isTrue();
        verify(vitals).refreshIfDue();
        verify(locks).release(SeoAuditScheduler.LOCK_NAME);

        when(locks.tryAcquire(eq(SeoAuditScheduler.LOCK_NAME), any())).thenReturn(false);
        assertThat(scheduler.tryStartManual()).isFalse();

        when(service.isEnabled()).thenReturn(false);
        assertThat(scheduler.tryStartManual()).isFalse();
    }
}
