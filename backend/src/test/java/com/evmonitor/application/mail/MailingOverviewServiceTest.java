package com.evmonitor.application.mail;

import com.evmonitor.application.mail.MailSendLog.CampaignGoal;
import com.evmonitor.application.mail.MailSendLog.CampaignVisits;
import com.evmonitor.application.mail.MailSendLog.SendStats;
import com.evmonitor.application.mail.MailingOverviewService.MailingRow;
import com.evmonitor.application.seo.PlausibleEventStatsService;
import com.evmonitor.application.seo.PlausibleEventStatsService.EventStat;
import com.evmonitor.application.seo.PlausibleEventStatsService.EventStats;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailingOverviewServiceTest {

    private final MailSendLog log = mock(MailSendLog.class);
    private final PlausibleEventStatsService events = mock(PlausibleEventStatsService.class);
    private final MailingOverviewService service = new MailingOverviewService(log, events);

    @Test
    void everyCatalogEntryGetsARow_withZerosWhenNothingHappened() {
        when(log.sendStats(any())).thenReturn(List.of());
        when(log.campaignVisits(any())).thenReturn(List.of());
        when(log.campaignGoals(any())).thenReturn(List.of());

        List<MailingRow> rows = service.overview(30);

        assertThat(rows).hasSize(MailType.values().length);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.sent()).isZero();
            assertThat(r.visits()).isZero();
            assertThat(r.goalVisitors()).isZero();
        });
    }

    @Test
    void row_joinsSendsVisitsAndOnlyItsOwnGoal() {
        LocalDateTime last = LocalDateTime.of(2026, 11, 1, 9, 0);
        when(log.sendStats(any())).thenReturn(List.of(new SendStats("re-engagement", 40, 2, last)));
        when(log.campaignVisits(any())).thenReturn(List.of(new CampaignVisits("re-engagement", 12)));
        when(log.campaignGoals(any())).thenReturn(List.of(
                new CampaignGoal("re-engagement", "log_created", 3),
                new CampaignGoal("re-engagement", "checkout_completed", 7)));

        MailingRow row = service.overview(30).stream().filter(r -> r.key().equals("re-engagement")).findFirst().orElseThrow();

        assertThat(row.sent()).isEqualTo(40);
        assertThat(row.failed()).isEqualTo(2);
        assertThat(row.lastSentAt()).isEqualTo(last);
        assertThat(row.visits()).isEqualTo(12);
        assertThat(row.goal()).isEqualTo("log_created");
        assertThat(row.goalVisitors()).isEqualTo(3);
    }

    @Test
    void goalSeenInPlausible_isTrue_unseenIsFalse_withoutGoalNull() {
        noActivity();
        when(events.last30Days()).thenReturn(new EventStats(true, LocalDate.now(), LocalDate.now(), List.of(
                new EventStat("log_created", 5, 7, true),
                new EventStat("checkout_completed", 2, 2, false))));

        List<MailingRow> rows = service.overview(30);

        assertThat(row(rows, "re-engagement").goalSeen()).isTrue();
        assertThat(row(rows, "euda-trial-ended").goalSeen()).isFalse();
        assertThat(row(rows, "verification").goalSeen()).isNull();
    }

    @Test
    void plausibleOffOrFailing_leavesGoalSeenUnknown() {
        noActivity();
        when(events.last30Days()).thenReturn(new EventStats(false, LocalDate.now(), LocalDate.now(), List.of()));
        assertThat(row(service.overview(30), "re-engagement").goalSeen()).isNull();

        when(events.last30Days()).thenThrow(new IllegalStateException("plausible down"));
        assertThat(row(service.overview(30), "re-engagement").goalSeen()).isNull();
    }

    private void noActivity() {
        when(log.sendStats(any())).thenReturn(List.of());
        when(log.campaignVisits(any())).thenReturn(List.of());
        when(log.campaignGoals(any())).thenReturn(List.of());
    }

    private static MailingRow row(List<MailingRow> rows, String key) {
        return rows.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void days_areClampedToASaneWindow() {
        when(log.sendStats(any())).thenReturn(List.of());
        when(log.campaignVisits(any())).thenReturn(List.of());
        when(log.campaignGoals(any())).thenReturn(List.of());

        assertThat(service.overview(-5)).isNotEmpty();
        assertThat(service.overview(100_000)).isNotEmpty();
        assertThat(MailingOverviewService.clampDays(-5)).isEqualTo(1);
        assertThat(MailingOverviewService.clampDays(100_000)).isEqualTo(365);
    }
}
