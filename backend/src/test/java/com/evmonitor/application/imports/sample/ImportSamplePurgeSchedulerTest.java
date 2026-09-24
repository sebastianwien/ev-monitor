package com.evmonitor.application.imports.sample;

import com.evmonitor.infrastructure.persistence.sample.ImportSampleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImportSamplePurgeSchedulerTest {

    @Test
    void deletesCopiesOlderThanTheRetention() {
        ImportSampleRepository repository = mock(ImportSampleRepository.class);

        new ImportSamplePurgeScheduler(repository, Duration.ofDays(180)).purgeExpired();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(threshold.capture());
        assertThat(threshold.getValue()).isBetween(LocalDateTime.now().minusDays(180).minusMinutes(1),
                LocalDateTime.now().minusDays(180).plusMinutes(1));
    }
}
