package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoInsights.BrandFilter;
import com.evmonitor.application.seo.SeoInsights.CampaignRow;
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
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.DayRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.SourceGoalTotals;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.SourceTotals;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository.RegistrationTotals;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository.UtmTotals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Lese-Seite des SEO-Tabs. GSC (vor dem Klick), Plausible (nach dem Klick) und Registrierungen (DB) werden
 * hier über den normalisierten Pfad zusammengeführt; Mediane und Befunde werden in Java gerechnet.
 * Der Zeitraum endet am letzten synchronisierten GSC-Tag, der Vorzeitraum ist gleich lang davor.
 */
@Service
public class SeoInsightsService {

    public static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 28, 90);
    static final String FINDING_UNDERRATED = "UNDERRATED";
    static final String FINDING_DEAD = "DEAD";
    static final String FINDING_LEAK = "LEAK";
    static final String FINDING_MONEY = "MONEY";
    static final String FINDING_STRIKING = "STRIKING_DISTANCE";
    static final String FINDING_CANNIBALIZED = "CANNIBALIZED";
    static final String GOOGLE_SOURCE = "Google";
    private static final int FALLBACK_LAG_DAYS = 2;

    private final GscDailyRepository repository;
    private final SeoAttributionRepository attribution;
    private final PlausibleDailyRepository plausible;
    private final SeoScoringProperties scoring;
    private final Clock clock;

    @Autowired
    public SeoInsightsService(GscDailyRepository repository, SeoAttributionRepository attribution,
                              PlausibleDailyRepository plausible, SeoScoringProperties scoring) {
        this(repository, attribution, plausible, scoring, Clock.systemDefaultZone());
    }

    SeoInsightsService(GscDailyRepository repository, SeoAttributionRepository attribution,
                       PlausibleDailyRepository plausible, SeoScoringProperties scoring, Clock clock) {
        this.repository = repository;
        this.attribution = attribution;
        this.plausible = plausible;
        this.scoring = scoring;
        this.clock = clock;
    }

    public Overview overview(int days) {
        Window w = window(days);
        Window prev = new Window(w.from.minusDays(days), w.from.minusDays(1));
        List<DayTotals> current = repository.dayTotals(w.from, w.to);
        List<DayTotals> previous = repository.dayTotals(prev.from, prev.to);

        Map<LocalDate, DayTotals> byDay = new LinkedHashMap<>();
        current.forEach(d -> byDay.put(d.day(), d));
        Map<LocalDate, DayRow> visitorsByDay = new HashMap<>();
        plausible.dayTotals(w.from, w.to).forEach(d -> visitorsByDay.put(d.day(), d));
        Map<LocalDate, Long> googleByDay = plausible.googleVisitsByDay(w.from, w.to);

        List<DayPoint> series = new ArrayList<>(days);
        for (LocalDate d = w.from; !d.isAfter(w.to); d = d.plusDays(1)) {
            DayTotals t = byDay.get(d);
            DayRow v = visitorsByDay.get(d);
            series.add(new DayPoint(d, t == null ? 0 : t.impressions(), t == null ? 0 : t.clicks(),
                    v == null ? 0 : v.visitors(), googleByDay.getOrDefault(d, 0L)));
        }
        return new Overview(days, w.from, w.to, totals(current, w), totals(previous, prev), series);
    }

    public List<PageRow> pages(int days, PageFilter filter) {
        Window w = window(days);
        List<PageTotals> totals = repository.pageTotals(w.from, w.to);
        List<PageTotals> visible = totals.stream().filter(p -> p.impressions() > 0).toList();
        double medianImpressions = median(visible, p -> p.impressions());
        double medianCtr = median(visible, p -> ctr(p.clicks(), p.impressions()));
        double medianClicks = median(visible, p -> p.clicks());
        Map<String, Behaviour> behaviour = behaviourByPath(w);
        double medianCtaRate = median(behaviour.values().stream().filter(b -> b.entries > 0).toList(), b -> b.ctaRate());
        Map<String, Long> registrations = attribution.registrationsByLandingPath(w.from, w.to);

        Map<String, PageRow> rows = new LinkedHashMap<>();
        for (PageTotals p : totals) {
            Behaviour b = behaviour.getOrDefault(p.path(), Behaviour.NONE);
            String finding = null;
            if (isLeak(p, b, medianClicks, medianCtaRate)) finding = FINDING_LEAK;
            else if (isUnderrated(p, medianImpressions, medianCtr)) finding = FINDING_UNDERRATED;
            rows.put(p.path(), new PageRow(p.path(), p.impressions(), p.clicks(), ctr(p.clicks(), p.impressions()),
                    round2(p.avgPosition()), b.entries, b.bounceRate, b.ctaRate(), registrations.getOrDefault(p.path(), 0L), finding));
        }
        // Seiten mit Einstiegen oder Registrierungen, aber ohne Google-Sichtbarkeit (Forum, Kampagne, direkt)
        Set<String> extra = new LinkedHashSet<>();
        behaviour.forEach((path, b) -> { if (b.entries > 0) extra.add(path); });
        extra.addAll(registrations.keySet());
        for (String path : extra) {
            if (rows.containsKey(path)) continue;
            Behaviour b = behaviour.getOrDefault(path, Behaviour.NONE);
            rows.put(path, new PageRow(path, 0, 0, 0, 0, b.entries, b.bounceRate, b.ctaRate(), registrations.getOrDefault(path, 0L), null));
        }
        markMoneyPages(rows);
        Set<String> alive = new HashSet<>();
        visible.forEach(p -> alive.add(p.path()));
        for (String path : repository.pathsWithImpressions(w.from.minusDays(scoring.getDeadLookbackDays()), w.from.minusDays(1))) {
            if (alive.contains(path)) continue;
            PageRow r = rows.get(path);
            rows.put(path, r == null ? new PageRow(path, 0, 0, 0, 0, 0, 0, 0, 0, FINDING_DEAD) : withFinding(r, FINDING_DEAD));
        }

        return rows.values().stream()
                .filter(r -> switch (filter) {
                    case ALL -> true;
                    case UNDERRATED -> FINDING_UNDERRATED.equals(r.finding());
                    case DEAD -> FINDING_DEAD.equals(r.finding());
                    case LEAK -> FINDING_LEAK.equals(r.finding());
                    case MONEY -> FINDING_MONEY.equals(r.finding());
                })
                .sorted(Comparator.comparingLong(PageRow::impressions).reversed()
                        .thenComparing(Comparator.comparingLong(PageRow::registrations).reversed())
                        .thenComparing(PageRow::path))
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
        Map<String, Behaviour> behaviour = behaviourByPath(w);
        double medianCtaRate = median(behaviour.values().stream().filter(b -> b.entries > 0).toList(), b -> b.ctaRate());

        List<QueryRow> rows = new ArrayList<>(accs.size());
        byQuery.forEach((query, a) -> {
            double position = a.position();
            boolean enough = a.impressions >= scoring.getMinImpressions();
            String finding = !enough ? null
                    : a.pageCount > 1 ? FINDING_CANNIBALIZED
                    : isStriking(position, a.impressions, medianImpressions) ? FINDING_STRIKING
                    : null;
            Behaviour target = a.topPath == null ? Behaviour.NONE : behaviour.getOrDefault(a.topPath, Behaviour.NONE);
            boolean adwords = enough && !a.brand && position > scoring.getAdwordsMinPosition()
                    && target.entries > 0 && target.ctaRate() > 0 && target.ctaRate() >= medianCtaRate;
            rows.add(new QueryRow(query, a.brand, a.impressions, a.clicks, ctr(a.clicks, a.impressions), position,
                    a.topPath, a.pageCount, adwords, finding));
        });
        return rows.stream()
                .filter(r -> switch (filter) {
                    case ALL -> true;
                    case STRIKING_DISTANCE -> FINDING_STRIKING.equals(r.finding());
                    case CANNIBALIZED -> FINDING_CANNIBALIZED.equals(r.finding());
                    case ADWORDS -> r.adwordsCandidate();
                })
                .sorted(Comparator.comparingLong(QueryRow::impressions).reversed().thenComparing(QueryRow::query))
                .limit(scoring.getMaxRows())
                .toList();
    }

    /** Quelle x Medium x Kampagne aus Plausible, DB-Registrierungen über das UTM-Tripel, Google organisch aus dem Verweis. */
    public List<CampaignRow> campaigns(int days) {
        Window w = window(days);
        Map<String, Long> ctaBySource = new HashMap<>();
        for (SourceGoalTotals g : plausible.sourceGoalTotals(w.from, w.to)) {
            if (!scoring.getCtaGoals().contains(g.goal())) continue;
            ctaBySource.merge(SeoAttributionRepository.utmKey(g.source(), g.utmMedium(), g.utmCampaign()), g.visitors(), Long::sum);
        }
        Map<String, UtmTotals> byUtm = attribution.registrationsByUtm(w.from, w.to);
        RegistrationTotals organic = attribution.registrationTotals(w.from, w.to);

        List<CampaignRow> rows = new ArrayList<>();
        for (SourceTotals s : plausible.sourceTotals(w.from, w.to)) {
            String key = SeoAttributionRepository.utmKey(s.source(), s.utmMedium(), s.utmCampaign());
            long cta = ctaBySource.getOrDefault(key, 0L);
            boolean hasUtm = !s.utmMedium().isEmpty() || !s.utmCampaign().isEmpty();
            boolean googleOrganic = GOOGLE_SOURCE.equals(s.source()) && !hasUtm;
            UtmTotals db = byUtm.get(key);
            long registrations = googleOrganic ? organic.organic() : db == null ? 0 : db.registrations();
            long trials = db == null ? 0 : db.trials();
            boolean attributed = hasUtm || googleOrganic || db != null;
            rows.add(new CampaignRow(s.source(), s.utmMedium(), s.utmCampaign(), s.visitors(), s.visits(), cta,
                    s.visitors() == 0 ? 0 : round4((double) cta / s.visitors()), registrations, trials, attributed));
        }
        return rows.stream()
                .sorted(Comparator.comparingLong(CampaignRow::visitors).reversed().thenComparing(CampaignRow::source))
                .limit(scoring.getMaxRows())
                .toList();
    }

    /** Letzter synchronisierter GSC-Tag; ohne Daten der Tag, bis zu dem GSC üblicherweise liefert. */
    public LocalDate dataUntil() {
        return repository.latestDay().orElseGet(() -> LocalDate.now(clock).minusDays(FALLBACK_LAG_DAYS));
    }

    public Optional<LocalDate> plausibleDataUntil() {
        return plausible.latestDay();
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

    /** Verhalten nach dem Klick je Pfad: Einstiege, Absprung (0-1), CTA-Besucher. */
    private record Behaviour(long entries, double bounceRate, long ctaVisitors) {
        static final Behaviour NONE = new Behaviour(0, 0, 0);

        double ctaRate() {
            return entries == 0 ? 0 : round4((double) ctaVisitors / entries);
        }
    }

    private Map<String, Behaviour> behaviourByPath(Window w) {
        Map<String, Long> cta = new HashMap<>();
        for (PlausibleDailyRepository.PageGoalTotals g : plausible.pageGoalTotals(w.from, w.to)) {
            if (scoring.getCtaGoals().contains(g.goal())) cta.merge(g.path(), g.visitors(), Long::sum);
        }
        Map<String, Behaviour> result = new HashMap<>();
        for (PlausibleDailyRepository.PageTotals p : plausible.pageTotals(w.from, w.to)) {
            result.put(p.path(), new Behaviour(p.entries(), round4(p.bounceRate() / 100.0), cta.getOrDefault(p.path(), 0L)));
        }
        return result;
    }

    private boolean isUnderrated(PageTotals p, double medianImpressions, double medianCtr) {
        return p.impressions() >= scoring.getMinImpressions()
                && p.avgPosition() >= scoring.getUnderratedMinPosition()
                && p.avgPosition() <= scoring.getUnderratedMaxPosition()
                && p.impressions() >= medianImpressions
                && ctr(p.clicks(), p.impressions()) < medianCtr;
    }

    private boolean isLeak(PageTotals p, Behaviour b, double medianClicks, double medianCtaRate) {
        return b.entries > 0 && p.clicks() > 0 && p.clicks() >= medianClicks
                && b.bounceRate >= scoring.getLeakBounceRate()
                && b.ctaRate() < medianCtaRate;
    }

    private boolean isStriking(double position, long impressions, double medianImpressions) {
        return position >= scoring.getStrikingMinPosition()
                && position <= scoring.getStrikingMaxPosition()
                && impressions >= medianImpressions;
    }

    /** Geldseiten: die obersten {@code moneyTopShare} nach Registrierungen je Klick unter Seiten mit Registrierungen. */
    private void markMoneyPages(Map<String, PageRow> rows) {
        List<PageRow> candidates = rows.values().stream()
                .filter(r -> r.registrations() > 0 && r.clicks() > 0 && r.impressions() >= scoring.getMinImpressions())
                .sorted(Comparator.comparingDouble((PageRow r) -> (double) r.registrations() / r.clicks()).reversed())
                .toList();
        int top = (int) Math.ceil(candidates.size() * scoring.getMoneyTopShare());
        for (int i = 0; i < top && i < candidates.size(); i++) {
            PageRow r = candidates.get(i);
            rows.put(r.path(), withFinding(r, FINDING_MONEY));
        }
    }

    private static PageRow withFinding(PageRow r, String finding) {
        return new PageRow(r.path(), r.impressions(), r.clicks(), r.ctr(), r.avgPosition(), r.entries(), r.bounceRate(),
                r.ctaRate(), r.registrations(), finding);
    }

    private Totals totals(List<DayTotals> days, Window w) {
        long impressions = 0;
        long clicks = 0;
        double weighted = 0;
        for (DayTotals d : days) {
            impressions += d.impressions();
            clicks += d.clicks();
            weighted += d.avgPosition() * d.impressions();
        }
        long visitors = plausible.dayTotals(w.from, w.to).stream().mapToLong(DayRow::visitors).sum();
        long google = plausible.googleVisitsByDay(w.from, w.to).values().stream().mapToLong(Long::longValue).sum();
        RegistrationTotals reg = attribution.registrationTotals(w.from, w.to);
        if (impressions == 0 && clicks == 0 && visitors == 0 && reg.registrations() == 0) return Totals.EMPTY;
        return new Totals(impressions, clicks, ctr(clicks, impressions), impressions > 0 ? round2(weighted / impressions) : 0,
                visitors, google, reg.registrations(), reg.organic());
    }

    private static <T> double median(List<T> items, ToDoubleFunction<T> value) {
        if (items.isEmpty()) return 0;
        double[] sorted = items.stream().mapToDouble(value).sorted().toArray();
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    private static double ctr(long clicks, long impressions) {
        return impressions == 0 ? 0 : round4((double) clicks / impressions);
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
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
