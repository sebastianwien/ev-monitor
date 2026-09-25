package com.evmonitor.application.ingest.event;

import com.evmonitor.application.ingest.event.ImportStatsResponse.DailyCount;
import com.evmonitor.application.ingest.event.ImportStatsResponse.Group;
import com.evmonitor.application.ingest.event.ImportStatsResponse.Health;
import com.evmonitor.application.ingest.event.ImportStatsResponse.TopError;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository.ErrorTotal;
import com.evmonitor.infrastructure.persistence.ingest.ImportEventRepository.OutcomeTotal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wertet das Import-Protokoll für den Admin-Tab aus. Die Ampel-Regel steht in {@link Health}. */
@Service
@RequiredArgsConstructor
public class ImportStatsService {

    /** Mehr als die Aufbewahrung gibt es nicht. */
    public static final int MAX_DAYS = 90;
    static final int TOP_ERRORS = 3;

    private final ImportEventRepository repository;

    @Transactional(readOnly = true)
    public ImportStatsResponse stats(int days) {
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();

        Map<List<String>, List<OutcomeTotal>> totalsByGroup = new LinkedHashMap<>();
        for (OutcomeTotal t : repository.outcomeTotalsSince(since)) {
            totalsByGroup.computeIfAbsent(List.of(t.provider(), t.channel()), k -> new ArrayList<>()).add(t);
        }
        Map<List<String>, List<ErrorTotal>> errorsByGroup = new LinkedHashMap<>();
        for (ErrorTotal e : repository.errorTotalsSince(since)) {
            errorsByGroup.computeIfAbsent(List.of(e.provider(), e.channel()), k -> new ArrayList<>()).add(e);
        }

        List<Group> groups = totalsByGroup.entrySet().stream()
                .map(entry -> toGroup(entry.getKey(), entry.getValue(), errorsByGroup.getOrDefault(entry.getKey(), List.of())))
                .toList();
        List<DailyCount> daily = repository.dailyTotalsSince(since).stream()
                .map(d -> new DailyCount(d.date(), d.outcome(), d.count()))
                .toList();
        return new ImportStatsResponse(days, groups, daily);
    }

    private static Group toGroup(List<String> key, List<OutcomeTotal> totals, List<ErrorTotal> errors) {
        Map<ImportEventOutcome, Long> outcomes = new EnumMap<>(ImportEventOutcome.class);
        long events = 0, sessionsImported = 0, sessionsSkipped = 0, sessionsFailed = 0, tripsImported = 0, tripsSkipped = 0;
        LocalDateTime lastEventAt = null;
        LocalDateTime lastSuccessAt = null;
        for (OutcomeTotal t : totals) {
            outcomes.merge(t.outcome(), t.events(), Long::sum);
            events += t.events();
            sessionsImported += t.sessionsImported();
            sessionsSkipped += t.sessionsSkipped();
            sessionsFailed += t.sessionsFailed();
            tripsImported += t.tripsImported();
            tripsSkipped += t.tripsSkipped();
            lastEventAt = latest(lastEventAt, t.lastAt());
            if (t.outcome().isSuccess()) lastSuccessAt = latest(lastSuccessAt, t.lastAt());
        }
        long failures = outcomes.getOrDefault(ImportEventOutcome.FAILED, 0L)
                + outcomes.getOrDefault(ImportEventOutcome.PARSE_ERROR, 0L);
        List<TopError> topErrors = errors.stream()
                .sorted(Comparator.comparingLong(ErrorTotal::count).reversed()
                        .thenComparing(ErrorTotal::lastAt, Comparator.reverseOrder()))
                .limit(TOP_ERRORS)
                .map(e -> new TopError(e.error(), e.count(), toInstant(e.lastAt())))
                .toList();
        return new Group(key.get(0), key.get(1), Health.of(failures, events),
                events, events == 0 ? 0 : (double) failures / events,
                sessionsImported, sessionsSkipped, sessionsFailed, tripsImported, tripsSkipped,
                outcomes, toInstant(lastEventAt), toInstant(lastSuccessAt), topErrors);
    }

    private static Instant toInstant(LocalDateTime serverTime) {
        return serverTime == null ? null : serverTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        return a == null || (b != null && b.isAfter(a)) ? b : a;
    }
}
