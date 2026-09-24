package com.evmonitor.application.imports.sample;

import com.evmonitor.application.imports.sample.ImportSampleService.Anonymized;
import com.evmonitor.application.imports.sample.ImportSampleService.Channel;
import com.evmonitor.application.imports.sample.ImportSampleService.Outcome;
import com.evmonitor.application.imports.sample.ImportSampleService.Provider;
import com.evmonitor.application.imports.sample.ImportSampleService.SampleMeta;
import com.evmonitor.infrastructure.persistence.sample.ImportSample;
import com.evmonitor.infrastructure.persistence.sample.ImportSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportSampleServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final String SHA = "a".repeat(64);

    private final ImportSampleRepository repository = mock(ImportSampleRepository.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private ImportSampleService service(boolean enabled, long maxBytes) {
        return new ImportSampleService(repository, txManager, enabled, maxBytes);
    }

    private SampleMeta meta(Outcome outcome, String error) {
        return new SampleMeta(USER, Provider.VW_EUDA, Channel.UPLOAD, "ID_3", SHA, outcome, 3, null, error);
    }

    @Test
    void disabled_neitherAnonymizesNorStores() {
        AtomicBoolean called = new AtomicBoolean();

        service(false, 1000).record(meta(Outcome.OK, null), () -> { called.set(true); return Optional.empty(); });

        assertThat(called).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void sameFileTwice_isStoredOnce_withoutAnonymizingAgain() {
        when(repository.existsByProviderAndContentSha256("VW_EUDA", SHA)).thenReturn(true);
        AtomicBoolean called = new AtomicBoolean();

        service(true, 1000).record(meta(Outcome.OK, null), () -> { called.set(true); return Optional.empty(); });

        assertThat(called).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void storesTheAnonymizedCopyWithItsOutcome() {
        service(true, 1000).record(meta(Outcome.UNREADABLE, "Format wird nicht unterstuetzt"),
                () -> Optional.of(new Anonymized("WVWZZZSAMPLE12345_1.zip", new byte[]{1, 2, 3})));

        ArgumentCaptor<ImportSample> saved = ArgumentCaptor.forClass(ImportSample.class);
        verify(repository).save(saved.capture());
        ImportSample s = saved.getValue();
        assertThat(s.getId()).isNotNull();
        assertThat(s.getUserId()).isEqualTo(USER);
        assertThat(s.getProvider()).isEqualTo("VW_EUDA");
        assertThat(s.getChannel()).isEqualTo("UPLOAD");
        assertThat(s.getCarModel()).isEqualTo("ID_3");
        assertThat(s.getContentSha256()).isEqualTo(SHA);
        assertThat(s.getFileName()).isEqualTo("WVWZZZSAMPLE12345_1.zip");
        assertThat(s.getSizeBytes()).isEqualTo(3);
        assertThat(s.getOutcome()).isEqualTo("UNREADABLE");
        assertThat(s.getSessionsDetected()).isEqualTo(3);
        assertThat(s.getError()).isEqualTo("Format wird nicht unterstuetzt");
        assertThat(s.getCreatedAt()).isNotNull();
    }

    @Test
    void cutsLongErrorsTo500Characters() {
        service(true, 1000).record(meta(Outcome.FAILED, "x".repeat(900)),
                () -> Optional.of(new Anonymized("f.zip", new byte[]{1})));

        ArgumentCaptor<ImportSample> saved = ArgumentCaptor.forClass(ImportSample.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getError()).hasSize(500);
    }

    @Test
    void failingAnonymizer_neverBreaksTheImport() {
        assertThatCode(() -> service(true, 1000).record(meta(Outcome.OK, null), () -> { throw new IOException("kaputt"); }))
                .doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }

    @Test
    void failingRepository_neverBreaksTheImport() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("race"));

        assertThatCode(() -> service(true, 1000).record(meta(Outcome.OK, null),
                () -> Optional.of(new Anonymized("f.zip", new byte[]{1})))).doesNotThrowAnyException();
    }

    @Test
    void emptyOrOversizedCopies_areNotStored() {
        ImportSampleService s = service(true, 2);

        s.record(meta(Outcome.OK, null), Optional::empty);
        s.record(meta(Outcome.OK, null), () -> Optional.of(new Anonymized("f.zip", new byte[]{1, 2, 3})));

        verify(repository, never()).save(any());
    }

    @Test
    void sha256_isHexOfTheWholeStream() throws Exception {
        String hex = ImportSampleService.sha256(new java.io.ByteArrayInputStream("abc".getBytes()));

        assertThat(hex).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
