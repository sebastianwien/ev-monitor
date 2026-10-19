package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoInsights.BrandFilter;
import com.evmonitor.application.seo.SeoInsights.BrandWeek;
import com.evmonitor.application.seo.SeoInsights.Breakdown;
import com.evmonitor.application.seo.SeoInsights.Opportunities;
import com.evmonitor.application.seo.SeoInsights.CampaignRow;
import com.evmonitor.application.seo.SeoInsights.DayPoint;
import com.evmonitor.application.seo.SeoInsights.ModelFilter;
import com.evmonitor.application.seo.SeoInsights.ModelLevel;
import com.evmonitor.application.seo.SeoInsights.ModelRow;
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
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository.DataBasis;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository.RegistrationTotals;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository.UtmTotals;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarBrand.CarModel;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    static final String FINDING_CONTENT_GAP = "CONTENT_GAP";
    static final String FINDING_INVISIBLE = "INVISIBLE";
    private static final Pattern MODEL_PATH = Pattern.compile("^/modelle/([^/]+)/([^/]+)$");
    private static final Pattern BRAND_PATH = Pattern.compile("^/modelle/([^/]+)$");
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

    public Overview overview(int days, SeoMarket market) {
        Window w = window(days);
        Window prev = new Window(w.from.minusDays(days), w.from.minusDays(1));
        List<DayTotals> current = dayTotals(w, market);
        List<DayTotals> previous = dayTotals(prev, market);

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
        boolean previousComplete = repository.earliestDay().map(first -> !first.isAfter(prev.from)).orElse(false);
        return new Overview(days, w.from, w.to, market, previousComplete, totals(current, w, market),
                totals(previous, prev, market), series);
    }

    /** Google-Tagessummen über die Pfade des Markts, Position nach Impressionen gewichtet. */
    private List<DayTotals> dayTotals(Window w, SeoMarket market) {
        Map<LocalDate, double[]> acc = new java.util.TreeMap<>();
        for (GscDailyRepository.PageRow r : repository.pageDays(w.from, w.to)) {
            if (!market.includes(r.path())) continue;
            double[] a = acc.computeIfAbsent(r.day(), k -> new double[3]);
            a[0] += r.clicks();
            a[1] += r.impressions();
            a[2] += r.avgPosition() * r.impressions();
        }
        List<DayTotals> result = new ArrayList<>(acc.size());
        acc.forEach((day, a) -> result.add(new DayTotals(day, (long) a[0], (long) a[1], a[1] > 0 ? a[2] / a[1] : 0)));
        return result;
    }

    /** Suchbegriffe, für die eine Seite erschienen ist, meiste Impressionen zuerst. */
    public List<Breakdown> pageQueries(int days, String path) {
        Window w = window(days);
        String normalized = SeoPath.normalize(path);
        return repository.queryPathTotals(w.from, w.to).stream()
                .filter(r -> r.path().equals(normalized))
                .map(r -> new Breakdown(r.query(), r.brand(), r.impressions(), r.clicks(), ctr(r.clicks(), r.impressions()),
                        round2(r.avgPosition())))
                .sorted(Comparator.comparingLong(Breakdown::impressions).reversed().thenComparing(Breakdown::key))
                .limit(scoring.getMaxRows())
                .toList();
    }

    /** Eigene Seiten, die für einen Suchbegriff erschienen sind, meiste Impressionen zuerst. */
    public List<Breakdown> queryPages(int days, String query) {
        Window w = window(days);
        return repository.queryPathTotals(w.from, w.to).stream()
                .filter(r -> r.query().equals(query))
                .map(r -> new Breakdown(r.path(), r.brand(), r.impressions(), r.clicks(), ctr(r.clicks(), r.impressions()),
                        round2(r.avgPosition())))
                .sorted(Comparator.comparingLong(Breakdown::impressions).reversed().thenComparing(Breakdown::key))
                .toList();
    }

    /** Brand- und Non-Brand-Suchen je Kalenderwoche (Montag) über die letzten {@code weeks} Wochen bis zum Datenstand. */
    public List<BrandWeek> brandTrend(int weeks) {
        if (weeks < 1 || weeks > 70) throw new IllegalArgumentException("Wochen müssen zwischen 1 und 70 liegen");
        LocalDate to = dataUntil();
        LocalDate firstWeek = to.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .minusWeeks(weeks - 1L);
        Map<LocalDate, long[]> acc = new java.util.TreeMap<>();
        for (int i = 0; i < weeks; i++) acc.put(firstWeek.plusWeeks(i), new long[4]);
        for (GscDailyRepository.BrandDay d : repository.brandDayTotals(firstWeek, to)) {
            long[] a = acc.get(d.day().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)));
            if (a == null) continue;
            int offset = d.brand() ? 0 : 2;
            a[offset] += d.impressions();
            a[offset + 1] += d.clicks();
        }
        List<BrandWeek> result = new ArrayList<>(weeks);
        acc.forEach((week, a) -> result.add(new BrandWeek(week, a[0], a[1], a[2], a[3])));
        return result;
    }

    /** Wie viele Zeilen je Befund im Zeitraum, ohne Zeilenobergrenze. */
    public Opportunities opportunities(int days, SeoMarket market) {
        Window w = window(days);
        Map<String, Integer> pages = new HashMap<>();
        pageRows(w, market).forEach(r -> { if (r.finding() != null) pages.merge(r.finding(), 1, Integer::sum); });
        List<QueryRow> queries = queryRows(w, BrandFilter.ALL, market);
        Map<String, Integer> models = new HashMap<>();
        modelRowsAll(w, ModelLevel.MODEL).forEach(r -> { if (r.finding() != null) models.merge(r.finding(), 1, Integer::sum); });
        return new Opportunities(
                pages.getOrDefault(FINDING_UNDERRATED, 0), pages.getOrDefault(FINDING_LEAK, 0),
                pages.getOrDefault(FINDING_DEAD, 0), pages.getOrDefault(FINDING_MONEY, 0),
                (int) queries.stream().filter(q -> FINDING_STRIKING.equals(q.finding())).count(),
                (int) queries.stream().filter(q -> FINDING_CANNIBALIZED.equals(q.finding())).count(),
                (int) queries.stream().filter(QueryRow::adwordsCandidate).count(),
                models.getOrDefault(FINDING_CONTENT_GAP, 0), models.getOrDefault(FINDING_INVISIBLE, 0));
    }

    public List<PageRow> pages(int days, PageFilter filter, SeoMarket market) {
        return pageRows(window(days), market).stream()
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

    /** Alle Seiten des Markts mit Befund, ungefiltert und ohne Obergrenze; Mediane innerhalb des Markts. */
    private java.util.Collection<PageRow> pageRows(Window w, SeoMarket market) {
        List<PageTotals> totals = repository.pageTotals(w.from, w.to).stream().filter(p -> market.includes(p.path())).toList();
        List<PageTotals> visible = totals.stream().filter(p -> p.impressions() > 0).toList();
        double medianImpressions = median(visible, p -> p.impressions());
        double medianCtr = median(visible, p -> ctr(p.clicks(), p.impressions()));
        double medianClicks = median(visible, p -> p.clicks());
        Map<String, Behaviour> behaviour = new HashMap<>();
        behaviourByPath(w).forEach((path, b) -> { if (market.includes(path)) behaviour.put(path, b); });
        double medianCtaRate = median(behaviour.values().stream().filter(b -> b.entries > 0).toList(), b -> b.ctaRate());
        Map<String, Long> registrations = new HashMap<>();
        attribution.registrationsByLandingPath(w.from, w.to).forEach((path, n) -> { if (market.includes(path)) registrations.put(path, n); });

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
            if (alive.contains(path) || !market.includes(path)) continue;
            PageRow r = rows.get(path);
            rows.put(path, r == null ? new PageRow(path, 0, 0, 0, 0, 0, 0, 0, 0, FINDING_DEAD) : withFinding(r, FINDING_DEAD));
        }
        return rows.values();
    }

    public List<QueryRow> queries(int days, BrandFilter brand, QueryFilter filter, SeoMarket market) {
        return queryRows(window(days), brand, market).stream()
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

    /** Alle Suchbegriffe mit Impressionen auf Seiten des Markts, ungefiltert und ohne Obergrenze. */
    private List<QueryRow> queryRows(Window w, BrandFilter brand, SeoMarket market) {
        Map<String, QueryAccumulator> byQuery = new LinkedHashMap<>();
        for (GscDailyRepository.QueryRow r : repository.queryPathTotals(w.from, w.to)) {
            if (!market.includes(r.path())) continue;
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
                    : a.pageCount > 1 && !a.brand ? FINDING_CANNIBALIZED
                    : isStriking(position, a.impressions, medianImpressions) ? FINDING_STRIKING
                    : null;
            Behaviour target = a.topPath == null ? Behaviour.NONE : behaviour.getOrDefault(a.topPath, Behaviour.NONE);
            boolean adwords = enough && !a.brand && position > scoring.getAdwordsMinPosition()
                    && target.entries > 0 && target.ctaRate() > 0 && target.ctaRate() >= medianCtaRate;
            rows.add(new QueryRow(query, a.brand, a.impressions, a.clicks, ctr(a.clicks, a.impressions), position,
                    a.topPath, a.pageCount, adwords, finding));
        });
        return rows;
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

    /**
     * Modellseiten: GSC je {@code /modelle/:brand/:model}, Registrierungen (erste Seite) und Datenbasis (Autos, Logs)
     * aus der DB. Modelle mit Daten, aber ohne Impressionen stehen mit 0 drin. Auf Markenebene zählen die
     * Modellseiten der Marke plus die Markenseite {@code /modelle/:brand}.
     */
    public List<ModelRow> models(int days, ModelLevel level, ModelFilter filter) {
        return modelRowsAll(window(days), level).stream()
                .filter(r -> switch (filter) {
                    case ALL -> true;
                    case CONTENT_GAP -> FINDING_CONTENT_GAP.equals(r.finding());
                    case INVISIBLE -> FINDING_INVISIBLE.equals(r.finding());
                })
                .sorted(Comparator.comparingLong(ModelRow::impressions).reversed()
                        .thenComparing(Comparator.comparingLong(ModelRow::logs).reversed())
                        .thenComparing(ModelRow::path))
                .limit(scoring.getMaxRows())
                .toList();
    }

    private List<ModelRow> modelRowsAll(Window w, ModelLevel level) {
        Map<String, DataBasis> basis = attribution.dataBasisByModel();
        // Pfadvarianten eines Modells (alte Schreibweise, Groß/klein) laufen auf einen Eintrag zusammen
        Map<CarModel, Gsc> byModel = new HashMap<>();
        Map<CarBrand, Gsc> byBrandPage = new HashMap<>();
        for (PageTotals p : repository.pageTotals(w.from, w.to)) {
            Optional<CarModel> model = modelOf(p.path());
            if (model.isPresent()) byModel.computeIfAbsent(model.get(), k -> new Gsc()).add(p);
            else brandOf(p.path()).ifPresent(b -> byBrandPage.computeIfAbsent(b, k -> new Gsc()).add(p));
        }
        attribution.registrationsByLandingPath(w.from, w.to).forEach((path, n) -> {
            Optional<CarModel> model = modelOf(path);
            if (model.isPresent()) byModel.computeIfAbsent(model.get(), k -> new Gsc()).registrations += n;
            else brandOf(path).ifPresent(b -> byBrandPage.computeIfAbsent(b, k -> new Gsc()).registrations += n);
        });
        basis.forEach((name, d) -> {
            if (d.logs() > 0) carModel(name).ifPresent(m -> byModel.computeIfAbsent(m, k -> new Gsc()));
        });
        byModel.keySet().removeIf(m -> m.getBrand() == CarBrand.SONSTIGE);

        return level == ModelLevel.BRAND
                ? brandRows(byModel, byBrandPage, basis)
                : modelRows(byModel, basis);
    }

    /** GSC-Summen und Registrierungen über alle Pfadvarianten einer Modell- oder Markenseite. */
    private static final class Gsc {
        long impressions;
        long clicks;
        double weighted;
        long registrations;

        void add(PageTotals p) {
            impressions += p.impressions();
            clicks += p.clicks();
            weighted += p.avgPosition() * p.impressions();
        }

        void add(Gsc o) {
            impressions += o.impressions;
            clicks += o.clicks;
            weighted += o.weighted;
            registrations += o.registrations;
        }

        double position() {
            return impressions == 0 ? 0 : round2(weighted / impressions);
        }
    }

    private List<ModelRow> modelRows(Map<CarModel, Gsc> byModel, Map<String, DataBasis> basis) {
        double medianImpressions = median(byModel.values().stream().filter(g -> g.impressions > 0).toList(), g -> g.impressions);
        List<ModelRow> rows = new ArrayList<>(byModel.size());
        byModel.forEach((model, g) -> {
            DataBasis d = basis.getOrDefault(model.name(), DataBasis.EMPTY);
            String finding = null;
            if (d.logs() == 0 && g.impressions >= scoring.getMinImpressions() && g.impressions >= medianImpressions) {
                finding = FINDING_CONTENT_GAP;
            } else if (d.logs() >= scoring.getModelMinLogs()
                    && (g.impressions < scoring.getMinImpressions() || g.position() > scoring.getStrikingMaxPosition())) {
                finding = FINDING_INVISIBLE;
            }
            rows.add(new ModelRow(model.getBrand().getDisplayString(), model.getDisplayName(), modelPath(model),
                    g.impressions, g.clicks, ctr(g.clicks, g.impressions), g.position(), g.registrations,
                    d.cars(), d.logs(), finding));
        });
        return rows;
    }

    /** Markenebene: alle Modellseiten der Marke plus die Markenseite, Datenbasis über die Modelle der Marke. */
    private List<ModelRow> brandRows(Map<CarModel, Gsc> byModel, Map<CarBrand, Gsc> byBrandPage, Map<String, DataBasis> basis) {
        Map<CarBrand, Gsc> gsc = new HashMap<>();
        Map<CarBrand, long[]> data = new HashMap<>();
        byModel.forEach((model, g) -> {
            gsc.computeIfAbsent(model.getBrand(), k -> new Gsc()).add(g);
            DataBasis d = basis.getOrDefault(model.name(), DataBasis.EMPTY);
            long[] cl = data.computeIfAbsent(model.getBrand(), k -> new long[2]);
            cl[0] += d.cars();
            cl[1] += d.logs();
        });
        byBrandPage.forEach((brand, g) -> gsc.computeIfAbsent(brand, k -> new Gsc()).add(g));
        List<ModelRow> rows = new ArrayList<>(gsc.size());
        gsc.forEach((brand, g) -> {
            long[] cl = data.getOrDefault(brand, new long[2]);
            rows.add(new ModelRow(brand.getDisplayString(), null, brandPath(brand), g.impressions, g.clicks,
                    ctr(g.clicks, g.impressions), g.position(), g.registrations, cl[0], cl[1], null));
        });
        return rows;
    }

    private static Optional<CarModel> modelOf(String path) {
        Matcher m = MODEL_PATH.matcher(path);
        if (!m.matches()) return Optional.empty();
        return CarModel.findBySlug(m.group(1).replace('_', ' '), m.group(2));
    }

    private static Optional<CarBrand> brandOf(String path) {
        Matcher b = BRAND_PATH.matcher(path);
        if (!b.matches()) return Optional.empty();
        String name = b.group(1).replace('_', ' ');
        for (CarBrand brand : CarBrand.values()) {
            if (brand != CarBrand.SONSTIGE && brand.getDisplayString().equalsIgnoreCase(name)) return Optional.of(brand);
        }
        return Optional.empty();
    }

    /** Kanonischer Pfad wie im Frontend: Leerzeichen in Marke und Modell als "_". */
    private static String modelPath(CarModel model) {
        return brandPath(model.getBrand()) + "/" + model.getDisplayName().replace(' ', '_');
    }

    private static String brandPath(CarBrand brand) {
        return "/modelle/" + brand.getDisplayString().replace(' ', '_');
    }

    private static Optional<CarModel> carModel(String enumName) {
        try {
            return Optional.of(CarModel.valueOf(enumName));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
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

    private Totals totals(List<DayTotals> days, Window w, SeoMarket market) {
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
        RegistrationTotals reg = registrations(w, market);
        if (impressions == 0 && clicks == 0 && visitors == 0 && reg.registrations() == 0) return Totals.EMPTY;
        return new Totals(impressions, clicks, ctr(clicks, impressions), impressions > 0 ? round2(weighted / impressions) : 0,
                visitors, google, reg.registrations(), reg.organic());
    }

    /** Alle Märkte: jedes Konto; ein Markt: nur Konten mit bekannter erster Seite in diesem Markt. */
    private RegistrationTotals registrations(Window w, SeoMarket market) {
        if (market == SeoMarket.ALL) return attribution.registrationTotals(w.from, w.to);
        long n = 0;
        long organic = 0;
        for (Map.Entry<String, RegistrationTotals> e : attribution.landingPathTotals(w.from, w.to).entrySet()) {
            if (!market.includes(e.getKey())) continue;
            n += e.getValue().registrations();
            organic += e.getValue().organic();
        }
        return new RegistrationTotals(n, organic);
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
