package com.evmonitor.application.ingest.event;

import com.evmonitor.domain.DataSource;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportEventRecorderTest {

    @Test
    void storageFailure_neverBreaksTheImport() {
        ImportEventRepository repository = mock(ImportEventRepository.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("fk"));

        ImportEventRecorder recorder = new ImportEventRecorder(repository, txManager);

        assertThatCode(() -> recorder.record(ImportEvent.of(DataSource.API_UPLOAD, UUID.randomUUID(), null)
                .outcome(ImportEventOutcome.IMPORTED).build()))
                .doesNotThrowAnyException();
    }

    @Test
    void null_isIgnored() {
        ImportEventRecorder recorder = new ImportEventRecorder(mock(ImportEventRepository.class),
                mock(PlatformTransactionManager.class));

        assertThatCode(() -> recorder.record(null)).doesNotThrowAnyException();
    }
}
