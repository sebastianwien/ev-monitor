package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoInsights.BrandFilter;
import com.evmonitor.application.seo.SeoInsights.DayPoint;
import com.evmonitor.application.seo.SeoInsights.Overview;
import com.evmonitor.application.seo.SeoInsights.PageFilter;
import com.evmonitor.application.seo.SeoInsights.PageRow;
import com.evmonitor.application.seo.SeoInsights.QueryFilter;
import com.evmonitor.application.seo.SeoInsights.QueryRow;
import com.evmonitor.application.seo.SeoInsights.Totals;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.DayTotals;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.PageTotals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Lese-Seite des SEO-Tabs. Aggregate kommen gruppiert aus der DB, Mediane und Befunde werden hier
 * gerechnet. Der Zeitraum endet am letzten synchronisierten Tag, der Vorzeitraum ist gleich lang davor.
 */
@Service
public class SeoInsightsService {

    public static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 28, 90);
    static final String FINDING_UNDERRATED = "UNDERRATED";
    static final String FINDING_DEAD = "DEAD";
    static final String FINDING_STRIKING = "STRIKING_DISTANCE";
    static final String FINDING_CANNIBALIZED = "CANNIBALIZED";
    private static final int FALLBACK_LAG_DAYS = 2;

    private final GscDailyRepository repository;
    private final SeoScoringProperties scoring;
    private final Clock clock;

    @Autowired
    public SeoInsightsService(GscDailyRepository repository, SeoScoringProperties scoring) {
        this(repository, scoring, Clock.systemDefaultZone());
    }

    SeoInsightsService(GscDailyRepository repository, SeoScoringProperties scoring, Clock clock) {
        this.repository = repository;
        this.scoring = scoring;
        this.clock = clock;
    }

    public Overview overview(int days) {
        Window w = window(days);
        List<DayTotals> current = repository.dayTotals(w.from, w.to);
        List<DayTotals> previous = repository.dayTotals(w.from.minusDays(days), w.from.minusDays(1));

        Map<LocalDate, DayTotals> byDay = new LinkedHashMap<>();
        current.forEach(d -> byDay.put(d.day(), d));
        List<DayPoint> series = new ArrayList<>(days);
        for (LocalDate d = w.from; !d.isAfter(w.to); d = d.plusDays(1)) {
            DayTotals t = byDay.get(d);
            series.add(new DayPoint(d, t == null ? 0 : t.impressions(), t == null ? 0 : t.clicks()));
        }
        return new Overview(days, w.from, w.to, totals(current), totals(previous), series);
    }

    public List<PageRow> pages(int days, PageFilter filter) {
        Window w = window(days);
        List<PageTotals> totals = repository.pageTotals(w.from, w.to);
        List<PageTotals> visible = totals.stream().filter(p -> p.impressions() > 0).toList();
        double medianImpressions = median(visible, p -> p.impressions());
        double medianCtr = median(visible, p -> ctr(p.clicks(), p.impressions()));

        List<PageRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PageTotals p : totals) {
            seen.add(p.path());
            String finding = isUnderrated(p, medianImpressions, medianCtr) ? FINDING_UNDERRATED : null;
            rows.add(new PageRow(p.path(), p.impressions(), p.clicks(), ctr(p.clicks(), p.impressions()),
                    round2(p.avgPosition()), finding));
        }
        Set<String> alive = new HashSet<>();
        visible.forEach(p -> alive.add(p.path()));
        List<String> previously = repository.pathsWithImpressions(w.from.minusDays(scoring.getDeadLookbackDays()), w.from.minusDays(1));
        for (String path : previously) {
            if (alive.contains(path)) continue;
            if (seen.contains(path)) {
                rows.replaceAll(r -> r.path().equals(path) ? withFinding(r, FINDING_DEAD) : r);
            } else {
                rows.add(new PageRow(path, 0, 0, 0, 0, FINDING_DEAD));
            }
        }

        return rows.stream()
                .filter(r -> switch (filter) {
                    case ALL -> true;
                    case UNDERRATED -> FINDING_UNDERRATED.equals(r.finding());
                    case DEAD -> FINDING_DEAD.equals(r.finding());
                })
                .sorted(Comparator.comparingLong(PageRow::impressions).reversed().thenComparing(PageRow::path))
                .limit(scoring.getMaxRows())
                .toList();
    }

    public List<QueryRow> queries(int days, BrandFilter brand, QueryFilter filter) {
        Window w = window(days);
        Map<String, QueryAccumulator> byQuery = new LinkedHashMap<>();
        for (GscDailyRepository.QueryRow r : repository.queryPathTotals(w.from, w.to)) {
            if (brand == BrandFilter.BRAND && !r.brand()) continue;
            if (brand == BrandFilter.NON_BRAND && r.brand()) continue;
            byQuery.computeIfAbsent(r.query(), k -> new QueryAccumulator(r.brand())).add(r);
        }
        List<QueryAccumulator> accs = new ArrayList<>(byQuery.values());
        double medianImpressions = median(accs, a -> a.impressions);

        List<QueryRow> rows = new ArrayList<>(accs.size());
        byQuery.forEach((query, a) -> {
            double position = a.position();
            String finding = a.impressions < scoring.getMinImpressions() ? null
                    : a.pageCount > 1 ? FINDING_CANNIBALIZED
                    : isStriking(position, a.impressions, medianImpressions) ? FINDING_STRIKING
                    : null;
            rows.add(new QueryRow(query, a.brand, a.impressions, a.clicks, ctr(a.clicks, a.impressions), position,
                    a.topPath, a.pageCount, finding));
        });
        return rows.stream()
                .filter(r -> switch (filter) {
                    case ALL -> true;
                    case STRIKING_DISTANCE -> FINDING_STRIKING.equals(r.finding());
                    case CANNIBALIZED -> FINDING_CANNIBALIZED.equals(r.finding());
                })
                .sorted(Comparator.comparingLong(QueryRow::impressions).reversed().thenComparing(QueryRow::query))
                .limit(scoring.getMaxRows())
                .toList();
    }

    /** Letzter synchronisierter Tag; ohne Daten der Tag, bis zu dem GSC üblicherweise liefert. */
    public LocalDate dataUntil() {
        return repository.latestDay().orElseGet(() -> LocalDate.now(clock).minusDays(FALLBACK_LAG_DAYS));
    }

    private record Window(LocalDate from, LocalDate to) {
    }

    private Window window(int days) {
        if (!ALLOWED_PERIODS.contains(days)) {
            throw new IllegalArgumentException("Zeitraum muss 7, 28 oder 90 Tage sein");
        }
        LocalDate to = dataUntil();
        return new Window(to.minusDays(days - 1L), to);
    }

    private boolean isUnderrated(PageTotals p, double medianImpressions, double medianCtr) {
        return p.impressions() >= scoring.getMinImpressions()
                && p.avgPosition() >= scoring.getUnderratedMinPosition()
                && p.avgPosition() <= scoring.getUnderratedMaxPosition()
                && p.impressions() >= medianImpressions
                && ctr(p.clicks(), p.impressions()) < medianCtr;
    }

    private boolean isStriking(double position, long impressions, double medianImpressions) {
        return position >= scoring.getStrikingMinPosition()
                && position <= scoring.getStrikingMaxPosition()
                && impressions >= medianImpressions;
    }

    private static PageRow withFinding(PageRow r, String finding) {
        return new PageRow(r.path(), r.impressions(), r.clicks(), r.ctr(), r.avgPosition(), finding);
    }

    private static Totals totals(List<DayTotals> days) {
        long impressions = 0;
        long clicks = 0;
        double weighted = 0;
        for (DayTotals d : days) {
            impressions += d.impressions();
            clicks += d.clicks();
            weighted += d.avgPosition() * d.impressions();
        }
        if (impressions == 0 && clicks == 0) return Totals.EMPTY;
        return new Totals(impressions, clicks, ctr(clicks, impressions), impressions > 0 ? round2(weighted / impressions) : 0);
    }

    private static <T> double median(List<T> items, ToDoubleFunction<T> value) {
        if (items.isEmpty()) return 0;
        double[] sorted = items.stream().mapToDouble(value).sorted().toArray();
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    private static double ctr(long clicks, long impressions) {
        return impressions == 0 ? 0 : Math.round((double) clicks / impressions * 10_000.0) / 10_000.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class QueryAccumulator {
        final boolean brand;
        long impressions;
        long clicks;
        double weighted;
        int pageCount;
        String topPath;
        long topClicks = -1;

        QueryAccumulator(boolean brand) {
            this.brand = brand;
        }

        void add(GscDailyRepository.QueryRow r) {
            impressions += r.impressions();
            clicks += r.clicks();
            weighted += r.avgPosition() * r.impressions();
            if (r.impressions() > 0) pageCount++;
            if (r.clicks() > topClicks || (r.clicks() == topClicks && r.impressions() > 0 && topPath == null)) {
                topClicks = r.clicks();
                topPath = r.path();
            }
        }

        double position() {
            return impressions == 0 ? 0 : round2(weighted / impressions);
        }
    }
}
