package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoSyncService.SyncResult;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Query;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Row;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.Batch;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.DayRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.PageGoalRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.PageRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.SourceGoalRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.SourceRow;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunEntity;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holt Plausible fensterweise in die eigene DB: je Fenster ein Call für Tage, Seiten, Einstiegsseiten,
 * je Goal die Einstiegsseiten, Quellen und Quellen x Goal. Pfade normalisiert wie GSC, zusammenfallende
 * Zeilen summiert (Absprung und Dauer nach Einstiegen gewichtet). Ein Fenster wird komplett ersetzt;
 * ein kaputtes Fenster bricht die anderen nicht ab.
 */
@Service
@Slf4j
public class PlausibleSyncService {

    public static final String SOURCE_PLAUSIBLE = "PLAUSIBLE";
    static final String NOT_SET = "(not set)";
    private static final String SEP = "\u0000";

    private final PlausibleClient client;
    private final PlausibleDailyRepository repository;
    private final SeoSyncRunRepository runs;
    private final PlausibleProperties properties;
    private final Clock clock;

    @Autowired
    public PlausibleSyncService(PlausibleClient client, PlausibleDailyRepository repository, SeoSyncRunRepository runs,
                                PlausibleProperties properties) {
        this(client, repository, runs, properties, Clock.systemDefaultZone());
    }

    PlausibleSyncService(PlausibleClient client, PlausibleDailyRepository repository, SeoSyncRunRepository runs,
                         PlausibleProperties properties, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.runs = runs;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return client.isEnabled();
    }

    public boolean hasAnyData() {
        return repository.hasAnyData();
    }

    /** Erster Tag des Backfills auf leerer Tabelle. */
    public LocalDate backfillFrom() {
        return properties.getBackfillFrom();
    }

    public SyncResult sync(LocalDate from, LocalDate to) {
        SeoSyncRunEntity run = new SeoSyncRunEntity(UUID.randomUUID(), SOURCE_PLAUSIBLE, LocalDateTime.now(clock), null,
                SeoSyncRunEntity.STATUS_RUNNING, 0, from, to, null);
        runs.save(run);

        int windows = 0;
        int failed = 0;
        int rows = 0;
        String lastError = null;
        int windowDays = Math.max(1, properties.getWindowDays());
        for (LocalDate start = from; !start.isAfter(to); start = start.plusDays(windowDays)) {
            LocalDate end = start.plusDays(windowDays - 1L);
            if (end.isAfter(to)) end = to;
            windows++;
            try {
                rows += syncWindow(start, end);
            } catch (Exception e) {
                failed++;
                lastError = start + " bis " + end + ": " + e.getMessage();
                log.warn("Plausible-Sync für {} bis {} fehlgeschlagen: {}", start, end, e.getMessage());
            }
        }

        int purged = repository.purgeBefore(LocalDate.now(clock).minusDays(properties.getRetentionDays()));
        if (purged > 0) log.info("Plausible-Sync: {} Zeilen älter als {} Tage gelöscht", purged, properties.getRetentionDays());

        String status = failed == 0 ? SeoSyncRunEntity.STATUS_SUCCESS
                : failed < windows ? SeoSyncRunEntity.STATUS_PARTIAL
                : SeoSyncRunEntity.STATUS_FAILED;
        run.setFinishedAt(LocalDateTime.now(clock));
        run.setStatus(status);
        run.setRowCount(rows);
        run.setError(lastError);
        runs.save(run);
        log.info("Plausible-Sync {} bis {}: {} Fenster, {} Zeilen, {} Fehler, Status {}", from, to, windows, rows, failed, status);
        return new SyncResult(run.getId(), windows, failed, rows, status);
    }

    int syncWindow(LocalDate from, LocalDate to) {
        List<DayRow> days = new ArrayList<>();
        for (Row r : query(List.of("visitors", "visits", "pageviews", "bounce_rate", "visit_duration"), from, to, List.of("time:day"), null)) {
            days.add(new DayRow(day(r), (int) r.metric(0), (int) r.metric(1), (int) r.metric(2), r.metric(3), (int) r.metric(4)));
        }

        Map<String, PageAcc> pages = new LinkedHashMap<>();
        for (Row r : query(List.of("visitors", "pageviews"), from, to, List.of("time:day", "event:page"), null)) {
            PageAcc a = pages.computeIfAbsent(day(r) + SEP + SeoPath.normalize(r.dim(1)), k -> new PageAcc());
            a.visitors += (int) r.metric(0);
            a.pageviews += (int) r.metric(1);
        }
        for (Row r : query(List.of("visitors", "visits", "bounce_rate", "visit_duration"), from, to, List.of("time:day", "visit:entry_page"), null)) {
            PageAcc a = pages.computeIfAbsent(day(r) + SEP + SeoPath.normalize(r.dim(1)), k -> new PageAcc());
            int entries = (int) r.metric(1);
            a.entries += entries;
            a.bounceWeighted += r.metric(2) * entries;
            a.durationWeighted += r.metric(3) * entries;
        }
        List<PageRow> pageRows = new ArrayList<>(pages.size());
        pages.forEach((key, a) -> {
            String[] parts = key.split(SEP, 2);
            pageRows.add(new PageRow(LocalDate.parse(parts[0]), parts[1], a.visitors, a.pageviews, a.entries,
                    a.entries == 0 ? 0 : a.bounceWeighted / a.entries, a.entries == 0 ? 0 : (int) Math.round(a.durationWeighted / a.entries)));
        });

        Map<String, Integer> pageGoals = new LinkedHashMap<>();
        for (String goal : properties.getGoals()) {
            for (Row r : query(List.of("visitors"), from, to, List.of("time:day", "visit:entry_page"), List.of(goal))) {
                pageGoals.merge(day(r) + SEP + SeoPath.normalize(r.dim(1)) + SEP + goal, (int) r.metric(0), Integer::sum);
            }
        }
        List<PageGoalRow> pageGoalRows = new ArrayList<>(pageGoals.size());
        pageGoals.forEach((key, n) -> {
            String[] parts = key.split(SEP, 3);
            pageGoalRows.add(new PageGoalRow(LocalDate.parse(parts[0]), parts[1], parts[2], n));
        });

        List<String> sourceDims = List.of("time:day", "visit:source", "visit:utm_medium", "visit:utm_campaign");
        List<SourceRow> sources = new ArrayList<>();
        for (Row r : query(List.of("visitors", "visits"), from, to, sourceDims, null)) {
            sources.add(new SourceRow(day(r), r.dim(1), unset(r.dim(2)), unset(r.dim(3)), (int) r.metric(0), (int) r.metric(1)));
        }
        List<SourceGoalRow> sourceGoals = new ArrayList<>();
        if (!properties.getGoals().isEmpty()) {
            List<String> dims = new ArrayList<>(sourceDims);
            dims.add("event:goal");
            for (Row r : query(List.of("visitors"), from, to, dims, properties.getGoals())) {
                sourceGoals.add(new SourceGoalRow(day(r), r.dim(1), unset(r.dim(2)), unset(r.dim(3)), r.dim(4), (int) r.metric(0)));
            }
        }

        Batch batch = new Batch(days, pageRows, pageGoalRows, sources, sourceGoals);
        repository.replaceRange(from, to, batch);
        return batch.size();
    }

    private List<Row> query(List<String> metrics, LocalDate from, LocalDate to, List<String> dims, List<String> goals) {
        return client.query(new Query(metrics, from, to, dims, goals));
    }

    private static LocalDate day(Row r) {
        return LocalDate.parse(r.dim(0).substring(0, 10));
    }

    static String unset(String value) {
        return value == null || NOT_SET.equals(value) ? "" : value;
    }

    private static final class PageAcc {
        int visitors;
        int pageviews;
        int entries;
        double bounceWeighted;
        double durationWeighted;
    }
}
