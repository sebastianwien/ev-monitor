package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.vehiclespec.MissingVehicleRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** DSGVO: die Kontakt-Mail wird 30 Tage nach Erledigung genullt, bei offenen Meldungen nach 90 Tagen. */
class MissingVehicleRequestPurgeSchedulerTest {

    @Test
    void nullsContactEmailOfRequestsDoneMoreThan30DaysAgo() {
        MissingVehicleRequestRepository repository = mock(MissingVehicleRequestRepository.class);

        new MissingVehicleRequestPurgeScheduler(repository).purgeContactEmails();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).purgeContactEmailsDoneBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(LocalDateTime.now().minusDays(30).minusMinutes(1),
                LocalDateTime.now().minusDays(30).plusMinutes(1));
    }

    @Test
    void nullsContactEmailOfOpenRequestsOlderThan90Days() {
        MissingVehicleRequestRepository repository = mock(MissingVehicleRequestRepository.class);

        new MissingVehicleRequestPurgeScheduler(repository).purgeContactEmails();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).purgeContactEmailsOpenCreatedBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(LocalDateTime.now().minusDays(90).minusMinutes(1),
                LocalDateTime.now().minusDays(90).plusMinutes(1));
    }
}
