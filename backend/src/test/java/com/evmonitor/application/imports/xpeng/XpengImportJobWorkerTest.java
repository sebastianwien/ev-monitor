package com.evmonitor.application.imports.xpeng;

import com.evmonitor.domain.xpeng.XpengImportFormat;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob;
import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XpengImportJobWorkerTest {

    @Mock XpengImportJobRepository jobRepo;
    @Mock XpengImportService importService;
    @Mock PlatformTransactionManager txManager;
    @TempDir Path tempDir;

    XpengImportJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new XpengImportJobWorker(jobRepo, importService, new TransactionTemplate(txManager));
    }

    @Test
    void drain_claimsQueuedJobsInOrderUntilQueueIsEmpty() {
        XpengImportJob first = queued();
        XpengImportJob second = queued();
        when(jobRepo.findNextQueuedForUpdate())
                .thenReturn(Optional.of(first), Optional.of(second), Optional.empty());
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.drain();

        assertEquals(XpengImportJob.Status.PROCESSING, first.getStatus());
        assertNotNull(first.getStartedAt(), "Claim setzt startedAt");
        var order = inOrder(importService);
        order.verify(importService).process(first);
        order.verify(importService).process(second);
    }

    @Test
    void drain_withEmptyQueue_doesNothing() {
        when(jobRepo.findNextQueuedForUpdate()).thenReturn(Optional.empty());

        worker.drain();

        verifyNoInteractions(importService);
    }

    @Test
    void drain_failureInOneJob_doesNotStopTheWorker() {
        XpengImportJob first = queued();
        XpengImportJob second = queued();
        when(jobRepo.findNextQueuedForUpdate())
                .thenReturn(Optional.of(first), Optional.of(second), Optional.empty());
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("boom")).when(importService).process(first);

        assertDoesNotThrow(() -> worker.drain());

        verify(importService).process(second);
    }

    @Test
    void recover_marksProcessingAsFailedAndKeepsQueuedJobsWithTempfile() throws Exception {
        Path existing = Files.createFile(tempDir.resolve("xpeng-a.zip"));
        XpengImportJob withFile = queued();
        withFile.setTempfilePath(existing.toString());
        XpengImportJob withoutFile = queued();
        withoutFile.setTempfilePath(tempDir.resolve("xpeng-gone.zip").toString());
        when(jobRepo.findAllByStatus(XpengImportJob.Status.QUEUED)).thenReturn(List.of(withFile, withoutFile));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.recoverInterruptedJobs();

        verify(jobRepo).markProcessingAsFailed(anyString(), any(LocalDateTime.class));
        assertEquals(XpengImportJob.Status.QUEUED, withFile.getStatus(), "Job mit Tempfile ueberlebt Neustart");
        assertEquals(XpengImportJob.Status.FAILED, withoutFile.getStatus(), "Job ohne Tempfile ist verloren");
        assertNotNull(withoutFile.getErrorMessage());
    }

    private static XpengImportJob queued() {
        return XpengImportJob.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).carId(UUID.randomUUID())
                .format(XpengImportFormat.CSV_ZIP)
                .status(XpengImportJob.Status.QUEUED).build();
    }
}
