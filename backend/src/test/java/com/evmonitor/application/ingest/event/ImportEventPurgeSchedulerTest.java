package com.evmonitor.application.ingest.event;

import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImportEventPurgeSchedulerTest {

    @Test
    void deletesEventsOlderThanTheRetention() {
        ImportEventRepository repository = mock(ImportEventRepository.class);

        new ImportEventPurgeScheduler(repository, Duration.ofDays(90)).purgeExpired();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(threshold.capture());
        assertThat(threshold.getValue()).isBetween(LocalDateTime.now().minusDays(90).minusMinutes(1),
                LocalDateTime.now().minusDays(90).plusMinutes(1));
    }
}
