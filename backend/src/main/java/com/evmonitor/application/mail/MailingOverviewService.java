package com.evmonitor.application.mail;

import com.evmonitor.application.mail.MailSendLog.CampaignGoal;
import com.evmonitor.application.mail.MailSendLog.CampaignVisits;
import com.evmonitor.application.mail.MailSendLog.SendStats;
import com.evmonitor.application.seo.PlausibleEventStatsService;
import com.evmonitor.application.seo.PlausibleEventStatsService.EventStat;
import com.evmonitor.application.seo.PlausibleEventStatsService.EventStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin-Tab "Mailings": Katalog plus Versand aus dem Protokoll plus Besuche und Goals aus Plausible. */
@Slf4j
@Service
public class MailingOverviewService {

    static final int MAX_DAYS = 365;
    public static final int RECENT_LIMIT = 50;

    private final MailSendLog sendLog;
    private final PlausibleEventStatsService events;

    public MailingOverviewService(MailSendLog sendLog, PlausibleEventStatsService events) {
        this.sendLog = sendLog;
        this.events = events;
    }

    /**
     * sent/failed aus dem Protokoll; visits/goalVisitors aus Plausible, Summe der Tage im Zeitraum.
     * goalSeen: Plausible meldete das Ziel-Goal in den letzten 30 Tagen als Goal (false = nicht angelegt oder
     * ohne Treffer); null ohne Ziel-Goal oder wenn Plausible nicht antwortet.
     */
    public record MailingRow(String key, MailType.Kind kind, String title, String purpose, String trigger, String audience,
                             String goal, Boolean goalSeen, long sent, long failed, LocalDateTime lastSentAt, long visits,
                             long goalVisitors) {}

    public List<MailingRow> overview(int days) {
        int d = clampDays(days);
        Map<String, SendStats> sends = sendLog.sendStats(LocalDateTime.now().minusDays(d)).stream()
                .collect(Collectors.toMap(SendStats::mailType, Function.identity(), (a, b) -> a));
        LocalDate since = LocalDate.now().minusDays(d);
        Map<String, Long> visits = sendLog.campaignVisits(since).stream()
                .collect(Collectors.toMap(CampaignVisits::campaign, CampaignVisits::visits, Long::sum));
        Map<String, Long> goals = sendLog.campaignGoals(since).stream()
                .collect(Collectors.toMap(g -> g.campaign() + "|" + g.goal(), CampaignGoal::visitors, Long::sum));

        Set<String> seenGoals = seenGoals();
        return Arrays.stream(MailType.values()).map(t -> {
            SendStats s = sends.get(t.key());
            long goalVisitors = t.goal() == null ? 0 : goals.getOrDefault(t.key() + "|" + t.goal(), 0L);
            // Treffer im gewählten Zeitraum belegen das Goal auch dann, wenn die letzten 30 Tage leer waren
            Boolean goalSeen = t.goal() == null ? null
                    : goalVisitors > 0 ? Boolean.TRUE
                    : seenGoals == null ? null : seenGoals.contains(t.goal());
            return new MailingRow(t.key(), t.kind(), t.title(), t.purpose(), t.trigger(), t.audience(), t.goal(), goalSeen,
                    s == null ? 0 : s.sent(), s == null ? 0 : s.failed(), s == null ? null : s.lastSentAt(),
                    visits.getOrDefault(t.key(), 0L), goalVisitors);
        }).toList();
    }

    public List<MailSendLog.RecentSend> recent(MailType type) {
        return sendLog.recent(type, RECENT_LIMIT);
    }

    /** Goals, die Plausible in den letzten 30 Tagen meldete; null, wenn Plausible aus ist oder nicht antwortet. */
    private Set<String> seenGoals() {
        try {
            EventStats stats = events.last30Days();
            if (!stats.enabled()) return null;
            return stats.events().stream().filter(EventStat::goal).map(EventStat::name).collect(Collectors.toSet());
        } catch (RuntimeException e) {
            log.warn("Plausible goals unavailable for mailing overview: {}", e.getMessage());
            return null;
        }
    }

    static int clampDays(int days) {
        return Math.max(1, Math.min(MAX_DAYS, days));
    }
}
