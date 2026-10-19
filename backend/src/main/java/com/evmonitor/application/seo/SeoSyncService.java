package com.evmonitor.application.seo;

import com.evmonitor.infrastructure.external.gsc.GscClient;
import com.evmonitor.infrastructure.external.gsc.GscClient.GscRow;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.PageRow;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.QueryRow;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.SegmentRow;
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
import java.util.function.Function;

/**
 * Holt die Search Console tageweise in die eigene DB: je Tag drei Abfragen (Seite, Suchbegriff x Seite,
 * Seite x Land x Gerät), Pfade normalisiert, Tag komplett ersetzt. Ein fehlender Tag bricht die anderen
 * nicht ab; nach drei Fehlern in Folge (typisch: Zugang kaputt) endet der Lauf.
 */
@Service
@Slf4j
public class SeoSyncService {

    public static final String SOURCE_GSC = "GSC";
    static final int MAX_CONSECUTIVE_FAILURES = 3;
    static final int MAX_QUERY_LENGTH = 500;

    public record SyncResult(UUID runId, int days, int failedDays, int rows, String status) {
    }

    private final GscClient client;
    private final GscDailyRepository repository;
    private final SeoSyncRunRepository runs;
    private final GscProperties properties;
    private final Clock clock;

    @Autowired
    public SeoSyncService(GscClient client, GscDailyRepository repository, SeoSyncRunRepository runs,
                          GscProperties properties) {
        this(client, repository, runs, properties, Clock.systemDefaultZone());
    }

    SeoSyncService(GscClient client, GscDailyRepository repository, SeoSyncRunRepository runs,
                   GscProperties properties, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.runs = runs;
        this.properties = properties;
        this.clock = clock;
    }

    /** false ohne Service-Account-Key: dann läuft kein Sync und der Tab zeigt den Leerzustand. */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    public SyncResult sync(LocalDate from, LocalDate to) {
        SeoSyncRunEntity run = SeoSyncRunEntity.start(SOURCE_GSC, LocalDateTime.now(clock), from, to);
        runs.save(run);

        int days = 0;
        int failed = 0;
        int consecutive = 0;
        int rows = 0;
        String lastError = null;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days++;
            try {
                rows += syncDay(day);
                consecutive = 0;
            } catch (Exception e) {
                failed++;
                consecutive++;
                lastError = day + ": " + e.getMessage();
                log.warn("GSC-Sync für {} fehlgeschlagen: {}", day, e.getMessage());
                if (consecutive >= MAX_CONSECUTIVE_FAILURES) {
                    log.error("GSC-Sync nach {} Fehlern in Folge abgebrochen", consecutive);
                    break;
                }
            }
        }

        int purged = repository.purgeBefore(LocalDate.now(clock).minusDays(properties.getRetentionDays()));
        if (purged > 0) log.info("GSC-Sync: {} Zeilen älter als {} Tage gelöscht", purged, properties.getRetentionDays());

        String status = failed == 0 ? SeoSyncRunEntity.STATUS_SUCCESS
                : failed < days ? SeoSyncRunEntity.STATUS_PARTIAL
                : SeoSyncRunEntity.STATUS_FAILED;
        run.setFinishedAt(LocalDateTime.now(clock));
        run.setStatus(status);
        run.setRowCount(rows);
        run.setError(lastError);
        runs.save(run);
        log.info("GSC-Sync {} bis {}: {} Tage, {} Zeilen, {} Fehler, Status {}", from, to, days, rows, failed, status);
        return new SyncResult(run.getId(), days, failed, rows, status);
    }

    private int syncDay(LocalDate day) {
        List<PageRow> pages = merge(client.query(day, day, List.of("page")),
                r -> SeoPath.normalize(r.keys().get(0)),
                (key, m) -> new PageRow(day, key, m.clicks, m.impressions, m.position()));
        List<QueryRow> queries = merge(client.query(day, day, List.of("query", "page")),
                r -> capQuery(r.keys().get(0)) + "\u0000" + SeoPath.normalize(r.keys().get(1)),
                (key, m) -> {
                    String[] parts = key.split("\u0000", 2);
                    return new QueryRow(day, parts[0], parts[1], m.clicks, m.impressions, m.position(), SeoPath.isBrand(parts[0]));
                });
        List<SegmentRow> segments = merge(client.query(day, day, List.of("page", "country", "device")),
                r -> SeoPath.normalize(r.keys().get(0)) + "\u0000" + r.keys().get(1) + "\u0000" + r.keys().get(2),
                (key, m) -> {
                    String[] parts = key.split("\u0000", 3);
                    return new SegmentRow(day, parts[0], parts[1], parts[2], m.clicks, m.impressions);
                });
        repository.replaceDay(day, pages, queries, segments);
        return pages.size() + queries.size() + segments.size();
    }

    private static String capQuery(String query) {
        return query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
    }

    /** Zeilen, die nach der Normalisierung auf denselben Schlüssel fallen, werden summiert. */
    private static <T> List<T> merge(List<GscRow> rows, Function<GscRow, String> keyOf,
                                     java.util.function.BiFunction<String, Accumulator, T> build) {
        Map<String, Accumulator> byKey = new LinkedHashMap<>();
        for (GscRow r : rows) {
            byKey.computeIfAbsent(keyOf.apply(r), k -> new Accumulator()).add(r);
        }
        List<T> out = new ArrayList<>(byKey.size());
        byKey.forEach((key, acc) -> out.add(build.apply(key, acc)));
        return out;
    }

    private static final class Accumulator {
        int clicks;
        int impressions;
        double weightedPosition;
        double plainPosition;
        int count;

        void add(GscRow r) {
            clicks += r.clicks();
            impressions += r.impressions();
            weightedPosition += r.position() * r.impressions();
            plainPosition += r.position();
            count++;
        }

        double position() {
            double p = impressions > 0 ? weightedPosition / impressions : plainPosition / Math.max(count, 1);
            return Math.round(p * 100.0) / 100.0;
        }
    }
}
