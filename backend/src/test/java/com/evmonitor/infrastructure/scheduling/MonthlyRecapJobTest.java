package com.evmonitor.infrastructure.scheduling;

import com.evmonitor.application.recap.MonthlyRecap;
import com.evmonitor.application.recap.MonthlyRecapService;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.github.GitHubIssueService;
import com.evmonitor.infrastructure.persistence.MonthlyRecapQueryRepository;
import com.evmonitor.infrastructure.persistence.MonthlyRecapQueryRepository.RecapCandidate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyRecapJobTest {

    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final LocalDate MONTH = AUGUST.atDay(1);

    private final MonthlyRecapQueryRepository repository = mock(MonthlyRecapQueryRepository.class);
    private final MonthlyRecapService recapService = mock(MonthlyRecapService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final GitHubIssueService gitHubIssueService = mock(GitHubIssueService.class);

    private final MonthlyRecapJob job = new MonthlyRecapJob(repository, recapService, emailService, gitHubIssueService);

    private final RecapCandidate candidate = new RecapCandidate(UUID.randomUUID(), UUID.randomUUID());
    private final MonthlyRecap recap = mock(MonthlyRecap.class);

    @Test
    void claimedCandidate_isMailedAndKeepsItsMarker() {
        when(repository.findCandidates(MONTH)).thenReturn(List.of(candidate));
        when(repository.claim(candidate.userId(), MONTH, candidate.carId())).thenReturn(true);
        when(recapService.build(candidate, AUGUST)).thenReturn(Optional.of(recap));

        MonthlyRecapJob.Report report = job.run(AUGUST);

        verify(emailService).sendMonthlyRecapEmail(recap);
        verify(repository, never()).release(any(), any());
        assertThat(report.sent()).isEqualTo(1);
    }

    @Test
    void candidateClaimedByAnotherRun_isSkipped() {
        when(repository.findCandidates(MONTH)).thenReturn(List.of(candidate));
        when(repository.claim(candidate.userId(), MONTH, candidate.carId())).thenReturn(false);

        MonthlyRecapJob.Report report = job.run(AUGUST);

        verify(recapService, never()).build(any(), any());
        verify(emailService, never()).sendMonthlyRecapEmail(any());
        assertThat(report.sent()).isZero();
    }

    @Test
    void nothingToTell_releasesTheMarker() {
        when(repository.findCandidates(MONTH)).thenReturn(List.of(candidate));
        when(repository.claim(candidate.userId(), MONTH, candidate.carId())).thenReturn(true);
        when(recapService.build(candidate, AUGUST)).thenReturn(Optional.empty());

        MonthlyRecapJob.Report report = job.run(AUGUST);

        verify(emailService, never()).sendMonthlyRecapEmail(any());
        verify(repository).release(candidate.userId(), MONTH);
        assertThat(report.skipped()).isEqualTo(1);
    }

    @Test
    void failedSend_releasesTheMarkerContinuesAndOpensAnIssue() {
        RecapCandidate second = new RecapCandidate(UUID.randomUUID(), UUID.randomUUID());
        MonthlyRecap secondRecap = mock(MonthlyRecap.class);
        when(repository.findCandidates(MONTH)).thenReturn(List.of(candidate, second));
        when(repository.claim(any(), any(), any())).thenReturn(true);
        when(recapService.build(candidate, AUGUST)).thenReturn(Optional.of(recap));
        when(recapService.build(second, AUGUST)).thenReturn(Optional.of(secondRecap));
        doThrow(new RuntimeException("smtp down")).when(emailService).sendMonthlyRecapEmail(recap);

        MonthlyRecapJob.Report report = job.run(AUGUST);

        verify(repository).release(candidate.userId(), MONTH);
        verify(emailService).sendMonthlyRecapEmail(secondRecap);
        assertThat(report.sent()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        verify(gitHubIssueService).createIssue(anyString(), anyString(), anyString());
    }
}
