package com.evmonitor.application.mail;

import com.evmonitor.application.mail.MailSendLog.CampaignGoal;
import com.evmonitor.application.mail.MailSendLog.CampaignVisits;
import com.evmonitor.application.mail.MailSendLog.SendStats;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin-Tab "Mailings": Katalog plus Versand aus dem Protokoll plus Besuche und Goals aus Plausible. */
@Service
public class MailingOverviewService {

    static final int MAX_DAYS = 365;
    public static final int RECENT_LIMIT = 50;

    private final MailSendLog log;

    public MailingOverviewService(MailSendLog log) {
        this.log = log;
    }

    /** sent/failed aus dem Protokoll; visits/goalVisitors aus Plausible, Summe der Tage im Zeitraum. */
    public record MailingRow(String key, MailType.Kind kind, String title, String purpose, String trigger, String audience,
                             String goal, long sent, long failed, LocalDateTime lastSentAt, long visits, long goalVisitors) {}

    public List<MailingRow> overview(int days) {
        int d = clampDays(days);
        Map<String, SendStats> sends = log.sendStats(LocalDateTime.now().minusDays(d)).stream()
                .collect(Collectors.toMap(SendStats::mailType, Function.identity(), (a, b) -> a));
        LocalDate since = LocalDate.now().minusDays(d);
        Map<String, Long> visits = log.campaignVisits(since).stream()
                .collect(Collectors.toMap(CampaignVisits::campaign, CampaignVisits::visits, Long::sum));
        Map<String, Long> goals = log.campaignGoals(since).stream()
                .collect(Collectors.toMap(g -> g.campaign() + "|" + g.goal(), CampaignGoal::visitors, Long::sum));

        return Arrays.stream(MailType.values()).map(t -> {
            SendStats s = sends.get(t.key());
            long goalVisitors = t.goal() == null ? 0 : goals.getOrDefault(t.key() + "|" + t.goal(), 0L);
            return new MailingRow(t.key(), t.kind(), t.title(), t.purpose(), t.trigger(), t.audience(), t.goal(),
                    s == null ? 0 : s.sent(), s == null ? 0 : s.failed(), s == null ? null : s.lastSentAt(),
                    visits.getOrDefault(t.key(), 0L), goalVisitors);
        }).toList();
    }

    public List<MailSendLog.RecentSend> recent(MailType type) {
        return log.recent(type, RECENT_LIMIT);
    }

    static int clampDays(int days) {
        return Math.max(1, Math.min(MAX_DAYS, days));
    }
}
