package com.evmonitor.infrastructure.scheduling;

import com.evmonitor.application.LeaderboardService;
import com.evmonitor.application.SpecChargingEfficiencyJob;
import com.evmonitor.application.SurveyService;
import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.SubscriptionTier;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.github.GitHubIssueService;
import com.evmonitor.infrastructure.weather.TemperatureBackfillJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSchedulerTest {

    private static final String SLUG = "autosync-satisfaction";

    @Mock private UserRepository userRepository;
    @Mock private CarRepository carRepository;
    @Mock private EvLogRepository evLogRepository;
    @Mock private EmailService emailService;
    @Mock private GitHubIssueService gitHubIssueService;
    @Mock private TemperatureBackfillJob temperatureBackfillJob;
    @Mock private com.evmonitor.infrastructure.weather.TripTemperatureBackfillJob tripTemperatureBackfillJob;
    @Mock private LeaderboardService leaderboardService;
    @Mock private SpecChargingEfficiencyJob specChargingEfficiencyJob;
    @Mock private SurveyService surveyService;

    private AppScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AppScheduler(userRepository, carRepository, evLogRepository, emailService,
                gitHubIssueService, temperatureBackfillJob, tripTemperatureBackfillJob, leaderboardService, specChargingEfficiencyJob,
                surveyService);
    }

    private User user(String username, String locale) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .id(UUID.randomUUID())
                .email(username + "@example.com").username(username).passwordHash("hash")
                .authProvider(AuthProvider.LOCAL).role("USER")
                .emailVerified(true).emailNotificationsEnabled(true)
                .referralCode("REFERRALCODE1")
                .subscriptionTier(SubscriptionTier.AUTOSYNC)
                .registrationLocale(locale)
                .createdAt(now).updatedAt(now)
                .build();
    }

    @Test
    void queriesCandidatesPurchased35DaysAgo() {
        when(userRepository.findAutoSyncSurveyCandidates(any())).thenReturn(List.of());

        scheduler.sendAutoSyncSatisfactionSurveys();

        ArgumentCaptor<LocalDate> dayCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(userRepository).findAutoSyncSurveyCandidates(dayCaptor.capture());
        assertThat(dayCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(35));
    }

    @Test
    void mailsCandidatesWhoHaveNotResponded_andSkipsResponders() {
        User fresh = user("fresh", "de");
        User responded = user("responded", "en");
        when(userRepository.findAutoSyncSurveyCandidates(any())).thenReturn(List.of(fresh, responded));
        when(surveyService.hasResponded(SLUG, fresh.getId())).thenReturn(false);
        when(surveyService.hasResponded(SLUG, responded.getId())).thenReturn(true);

        scheduler.sendAutoSyncSatisfactionSurveys();

        verify(emailService).sendAutoSyncSatisfactionEmail("fresh@example.com", "fresh", "de");
        verify(emailService, never()).sendAutoSyncSatisfactionEmail(eq("responded@example.com"), any(), any());
    }

    @Test
    void oneFailedSend_doesNotAbortRemainingCandidates() {
        User boom = user("boom", "de");
        User ok = user("ok", "en");
        when(userRepository.findAutoSyncSurveyCandidates(any())).thenReturn(List.of(boom, ok));
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendAutoSyncSatisfactionEmail("boom@example.com", "boom", "de");

        scheduler.sendAutoSyncSatisfactionSurveys();

        // The second candidate must still be mailed despite the first one throwing.
        verify(emailService).sendAutoSyncSatisfactionEmail("ok@example.com", "ok", "en");
        // The failure is surfaced (not swallowed) via a GitHub issue.
        verify(gitHubIssueService).createIssue(anyString(), anyString(), anyString());
    }
}
