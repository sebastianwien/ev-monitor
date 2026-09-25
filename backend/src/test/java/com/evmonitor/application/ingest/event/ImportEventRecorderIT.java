package com.evmonitor.application.ingest.event;

import com.evmonitor.domain.DataSource;
import com.evmonitor.infrastructure.persistence.ingest.ImportEvent;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Das Protokoll wird erst nach Ende der Import-Transaktion geschrieben: bei Commit wie berechnet,
 * bei Rollback als FAILED - ein zurückgerollter Import darf nicht als Erfolg im Tab stehen.
 */
class ImportEventRecorderIT extends AbstractIntegrationTest {

    @Autowired ImportEventRecorder recorder;
    @Autowired ImportEventRepository repository;
    @Autowired PlatformTransactionManager transactionManager;

    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        repository.deleteByUserId(userId);
    }

    private ImportEvent imported() {
        return ImportEvent.of(DataSource.API_UPLOAD, userId, null)
                .outcome(ImportEventOutcome.IMPORTED).sessionsImported(3).sessionsSkipped(1).build();
    }

    private List<ImportEvent> rows() {
        return repository.findAll().stream().filter(e -> userId.equals(e.getUserId())).toList();
    }

    @Test
    void withoutTransaction_writesImmediately() {
        recorder.record(imported());

        assertThat(rows()).singleElement().satisfies(e -> {
            assertThat(e.getOutcome()).isEqualTo(ImportEventOutcome.IMPORTED);
            assertThat(e.getProvider()).isEqualTo("PUBLIC_API");
            assertThat(e.getChannel()).isEqualTo("UPLOAD");
            assertThat(e.getDataSource()).isEqualTo("API_UPLOAD");
            assertThat(e.getSessionsImported()).isEqualTo(3);
            assertThat(e.getSessionsSkipped()).isEqualTo(1);
            assertThat(e.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void insideTransaction_writesOnlyAfterCommit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recorder.record(imported());
            assertThat(rows()).isEmpty();
        });

        assertThat(rows()).singleElement().extracting(ImportEvent::getOutcome).isEqualTo(ImportEventOutcome.IMPORTED);
    }

    @Test
    void rolledBack_isRecordedAsFailedWithoutImportedCounts() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recorder.record(imported());
            status.setRollbackOnly();
        });

        assertThat(rows()).singleElement().satisfies(e -> {
            assertThat(e.getOutcome()).isEqualTo(ImportEventOutcome.FAILED);
            assertThat(e.getError()).isEqualTo(ImportEventRecorder.ROLLED_BACK);
            assertThat(e.getSessionsImported()).isZero();
            assertThat(e.getSessionsSkipped()).isEqualTo(1);
        });
    }

    @Test
    void rolledBack_keepsAnAlreadyNegativeOutcome() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recorder.record(ImportEvent.of(DataSource.XPENG_IMPORT, userId, null)
                    .outcome(ImportEventOutcome.PARSE_ERROR).error("XpengParseException: kaputt").build());
            status.setRollbackOnly();
        });

        assertThat(rows()).singleElement().satisfies(e -> {
            assertThat(e.getOutcome()).isEqualTo(ImportEventOutcome.PARSE_ERROR);
            assertThat(e.getError()).isEqualTo("XpengParseException: kaputt");
        });
    }
}
