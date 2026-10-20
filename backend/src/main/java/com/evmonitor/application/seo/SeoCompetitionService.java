package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoCompetition.CompetitorRow;
import com.evmonitor.application.seo.SeoCompetition.IdeaRow;
import com.evmonitor.application.seo.SeoCompetition.KeywordRow;
import com.evmonitor.application.seo.SeoCompetition.SerpEntry;
import com.evmonitor.application.seo.SeoCompetition.SerpQueryRow;
import com.evmonitor.application.seo.SeoCompetition.View;
import com.evmonitor.application.seo.SeoInsights.BrandFilter;
import com.evmonitor.application.seo.SeoInsights.QueryFilter;
import com.evmonitor.application.seo.SeoInsights.QueryRow;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.KeywordData;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.Paid;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.SerpItem;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.KeywordIdea;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.KeywordMetric;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.SerpRow;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunEntity;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Unter-Tab "Wettbewerb": wöchentlich die Top 10 bei Google für unsere wichtigsten Non-Brand-Begriffe der deutschen
 * Website, monatlich Suchvolumen und CPC sowie Begriffsideen. Holt nur, was fällig ist, und bricht vor dem
 * Monatsbudget ab. Kosten stehen am Lauf in {@code seo_sync_run}.
 */
@Service
@Slf4j
public class SeoCompetitionService {

    public static final String SOURCE_SERP = "SERP";
    public static final String UP_TO_DATE = "UP_TO_DATE";
    /** Geschätzte Kosten je Abruf (Live-Modus, Preise Oktober 2026), nur für die Budgetprüfung vorab. */
    static final double EST_SERP = 0.002;
    static final double EST_VOLUME = 0.075;
    static final double EST_SUGGESTION_TASK = 0.012;
    static final double EST_SUGGESTION_ITEM = 0.00012;
    static final int VOLUME_BATCH = 1000;
    static final int MAX_CONSECUTIVE_ERRORS = 3;
    /** Google Ads nimmt höchstens 80 Zeichen und 10 Wörter je Begriff. */
    static final int MAX_KEYWORD_LENGTH = 80;
    static final int MAX_KEYWORD_WORDS = 10;
    static final int COVERAGE_DAYS = 90;

    /** status SUCCESS, PARTIAL, FAILED oder UP_TO_DATE (nichts fällig, nichts abgerufen). */
    public record RunResult(UUID runId, int serpQueries, int metrics, int ideas, double costUsd, String status, String message) {
    }

    /** Konfiguration und Uhr gebündelt, damit der Konstruktor bei fünf Parametern bleibt. */
    record Deps(SeoCompetitionProperties properties, Clock clock) {
    }

    private final DataForSeoClient client;
    private final SeoCompetitionRepository repository;
    private final SeoInsightsService insights;
    private final SeoSyncRunRepository runs;
    private final Deps deps;

    @Autowired
    public SeoCompetitionService(DataForSeoClient client, SeoCompetitionRepository repository, SeoInsightsService insights,
                                 SeoSyncRunRepository runs, SeoCompetitionProperties properties) {
        this(client, repository, insights, runs, new Deps(properties, Clock.systemDefaultZone()));
    }

    SeoCompetitionService(DataForSeoClient client, SeoCompetitionRepository repository, SeoInsightsService insights,
                          SeoSyncRunRepository runs, Deps deps) {
        this.client = client;
        this.repository = repository;
        this.insights = insights;
        this.runs = runs;
        this.deps = deps;
    }

    public boolean isEnabled() {
        return client.isEnabled();
    }

    /** Ein Lauf mit Budget und Fehlerzähler; die Abrufe teilen sich beides. */
    private final class Spend {
        final double budgetLeft;
        double cost;
        boolean budgetHit;
        int errors;
        String lastError;

        Spend(double budgetLeft) {
            this.budgetLeft = budgetLeft;
        }

        boolean allows(double estimate) {
            if (cost + estimate > budgetLeft) {
                budgetHit = true;
                return false;
            }
            return true;
        }

        void fail(Exception e) {
            errors++;
            lastError = e.getMessage();
            log.warn("DataForSEO-Abruf fehlgeschlagen: {}", e.getMessage());
        }
    }

    public RunResult run() {
        LocalDate today = LocalDate.now(deps.clock());
        LocalDate week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate month = today.withDayOfMonth(1);
        boolean serpDue = repository.latestSerpWeek().filter(week::equals).isEmpty();
        boolean monthDue = repository.latestMetricsMonth().filter(month::equals).isEmpty();
        if (!serpDue && !monthDue) return new RunResult(null, 0, 0, 0, 0, UP_TO_DATE, "Alle Daten sind aktuell.");

        SeoSyncRunEntity run = SeoSyncRunEntity.start(SOURCE_SERP, LocalDateTime.now(deps.clock()), today, today);
        runs.save(run);
        double spent = repository.costSince(SOURCE_SERP, month.atStartOfDay());
        Spend spend = new Spend(Math.max(0, deps.properties().getMonthlyBudgetUsd() - spent));

        int serp = serpDue ? fetchSerp(week, spend) : 0;
        int[] monthly = monthDue && spend.errors < MAX_CONSECUTIVE_ERRORS ? fetchMonthly(month, spend) : new int[2];
        int purged = repository.purgeBefore(today.minusDays(deps.properties().getRetentionDays()));
        if (purged > 0) log.info("Wettbewerb: {} alte Zeilen gelöscht", purged);

        boolean anything = serp + monthly[0] + monthly[1] > 0;
        // Budget erreicht ist kein Fehler; fehlgeschlagen nur, wenn Fehler auftraten und gar nichts ankam
        String status;
        if (spend.errors == 0 && !spend.budgetHit) status = SeoSyncRunEntity.STATUS_SUCCESS;
        else if (anything || spend.errors == 0) status = SeoSyncRunEntity.STATUS_PARTIAL;
        else status = SeoSyncRunEntity.STATUS_FAILED;
        String message = spend.budgetHit ? "Monatsbudget von " + deps.properties().getMonthlyBudgetUsd() + " US-Dollar erreicht, Rest im nächsten Monat."
                : spend.lastError;
        run.setFinishedAt(LocalDateTime.now(deps.clock()));
        run.setStatus(status);
        run.setRowCount(serp + monthly[0] + monthly[1]);
        run.setError(message);
        run.setCostUsd(BigDecimal.valueOf(spend.cost).setScale(4, java.math.RoundingMode.HALF_UP));
        runs.save(run);
        log.info("Wettbewerb {}: {} Suchbegriffe, {} Kennzahlen, {} Ideen, {} USD, Status {}", today, serp, monthly[0], monthly[1],
                spend.cost, status);
        return new RunResult(run.getId(), serp, monthly[0], monthly[1], round4(spend.cost), status, message);
    }

    /** Top 10 für die wichtigsten Non-Brand-Begriffe; schreibt die Woche nur, wenn etwas ankam. */
    private int fetchSerp(LocalDate week, Spend spend) {
        List<SerpRow> rows = new ArrayList<>();
        int fetched = 0;
        int consecutive = 0;
        for (QueryRow q : topNonBrand(deps.properties().getTopQueries())) {
            if (!spend.allows(EST_SERP)) break;
            try {
                Paid<List<SerpItem>> result = client.serp(q.query());
                spend.cost += result.costUsd();
                for (SerpItem item : result.value()) {
                    rows.add(new SerpRow(q.query(), item.rank(), cut(item.domain(), 255), cut(item.url(), 2000), cut(item.title(), 500)));
                }
                fetched++;
                consecutive = 0;
            } catch (Exception e) {
                spend.fail(e);
                if (++consecutive >= MAX_CONSECUTIVE_ERRORS) break;
            }
        }
        if (fetched > 0) repository.replaceSerpWeek(week, rows);
        return fetched;
    }

    /** Volumen für alle deutschen Begriffe mit Impressionen plus Ideen aus den Top-Begriffen. {Kennzahlen, Ideen}. */
    private int[] fetchMonthly(LocalDate month, Spend spend) {
        Map<String, KeywordMetric> metrics = new LinkedHashMap<>();
        List<String> keywords = insights.queries(28, BrandFilter.ALL, QueryFilter.ALL, SeoMarket.DE).stream()
                .map(QueryRow::query)
                .filter(SeoCompetitionService::acceptedByGoogleAds)
                .limit(deps.properties().getVolumeMaxKeywords())
                .toList();
        for (int i = 0; i < keywords.size(); i += VOLUME_BATCH) {
            if (!spend.allows(EST_VOLUME)) break;
            try {
                Paid<List<KeywordData>> result = client.searchVolume(keywords.subList(i, Math.min(i + VOLUME_BATCH, keywords.size())));
                spend.cost += result.costUsd();
                result.value().forEach(k -> metrics.put(k.keyword(), new KeywordMetric(k.keyword(), k.searchVolume(), k.cpc(), k.competition())));
            } catch (Exception e) {
                spend.fail(e);
                break;
            }
        }

        Map<String, KeywordIdea> ideas = new LinkedHashMap<>();
        int limit = deps.properties().getSuggestionLimit();
        for (QueryRow seed : topNonBrand(deps.properties().getSuggestionSeeds())) {
            if (!spend.allows(EST_SUGGESTION_TASK + limit * EST_SUGGESTION_ITEM)) break;
            try {
                Paid<List<KeywordData>> result = client.suggestions(seed.query(), limit);
                spend.cost += result.costUsd();
                for (KeywordData k : result.value()) {
                    String keyword = cut(k.keyword(), 500);
                    KeywordIdea existing = ideas.get(keyword);
                    if (existing == null || volume(k.searchVolume()) > volume(existing.searchVolume())) {
                        ideas.put(keyword, new KeywordIdea(keyword, seed.query(), k.searchVolume(), k.cpc()));
                    }
                    metrics.putIfAbsent(keyword, new KeywordMetric(keyword, k.searchVolume(), k.cpc(), k.competition()));
                }
            } catch (Exception e) {
                spend.fail(e);
                break;
            }
        }
        if (!metrics.isEmpty()) repository.replaceMetricsMonth(month, new ArrayList<>(metrics.values()));
        if (!ideas.isEmpty()) repository.replaceIdeasMonth(month, new ArrayList<>(ideas.values()));
        return new int[] {metrics.size(), ideas.size()};
    }

    private List<QueryRow> topNonBrand(int n) {
        return insights.queries(28, BrandFilter.NON_BRAND, QueryFilter.ALL, SeoMarket.DE).stream()
                .sorted(Comparator.comparingLong(QueryRow::impressions).reversed())
                .limit(Math.max(0, n))
                .toList();
    }

    static boolean acceptedByGoogleAds(String keyword) {
        return keyword != null && !keyword.isBlank() && keyword.length() <= MAX_KEYWORD_LENGTH
                && keyword.trim().split("\\s+").length <= MAX_KEYWORD_WORDS;
    }

    /** Lese-Sicht für den Unter-Tab; days steuert nur die Impressionen je Begriff. */
    public View view(int days) {
        LocalDate month = LocalDate.now(deps.clock()).withDayOfMonth(1);
        double cost = repository.costSince(SOURCE_SERP, month.atStartOfDay());
        String own = deps.properties().getOwnDomain().toLowerCase();
        LocalDate week = repository.latestSerpWeek().orElse(null);
        LocalDate metricsMonth = repository.latestMetricsMonth().orElse(null);

        Map<String, KeywordMetric> metrics = new HashMap<>();
        if (metricsMonth != null) repository.metrics(metricsMonth).forEach(m -> metrics.put(m.query(), m));
        Map<String, Long> impressions = new HashMap<>();
        insights.queries(days, BrandFilter.ALL, QueryFilter.ALL, SeoMarket.DE).forEach(q -> impressions.put(q.query(), q.impressions()));

        Map<String, List<SerpRow>> byQuery = new LinkedHashMap<>();
        if (week != null) repository.serpRows(week).forEach(r -> byQuery.computeIfAbsent(r.query(), k -> new ArrayList<>()).add(r));

        Map<String, int[]> competitor = new HashMap<>();   // queries, ahead, rankSum, best
        List<SerpQueryRow> queries = new ArrayList<>();
        byQuery.forEach((query, rows) -> {
            SerpRow ours = rows.stream().filter(r -> isOwn(r.domain(), own)).min(Comparator.comparingInt(SerpRow::rank)).orElse(null);
            Set<String> seen = new HashSet<>();
            for (SerpRow r : rows) {
                if (isOwn(r.domain(), own) || !seen.add(r.domain())) continue;
                int[] c = competitor.computeIfAbsent(r.domain(), k -> new int[] {0, 0, 0, Integer.MAX_VALUE});
                c[0]++;
                if (ours == null || r.rank() < ours.rank()) c[1]++;
                c[2] += r.rank();
                c[3] = Math.min(c[3], r.rank());
            }
            KeywordMetric m = metrics.get(query);
            queries.add(new SerpQueryRow(query, impressions.getOrDefault(query, 0L), ours == null ? null : ours.rank(),
                    ours == null ? null : ours.url(), m == null ? null : m.searchVolume(), m == null ? null : m.cpc(),
                    rows.stream().map(r -> new SerpEntry(r.rank(), r.domain(), r.url(), r.title(), isOwn(r.domain(), own))).toList()));
        });
        queries.sort(Comparator.comparingLong(SerpQueryRow::impressions).reversed().thenComparing(SerpQueryRow::query));

        List<CompetitorRow> competitors = new ArrayList<>();
        competitor.forEach((domain, c) -> competitors.add(new CompetitorRow(domain, c[0], c[1], round2((double) c[2] / c[0]), c[3])));
        competitors.sort(Comparator.comparingInt(CompetitorRow::aheadOfUs).reversed()
                .thenComparing(Comparator.comparingInt(CompetitorRow::queries).reversed())
                .thenComparing(CompetitorRow::domain));

        List<IdeaRow> ideas = new ArrayList<>();
        LocalDate ideasMonth = repository.latestIdeasMonth().orElse(null);
        if (ideasMonth != null) {
            Set<String> covered = new HashSet<>();
            insights.queries(COVERAGE_DAYS, BrandFilter.ALL, QueryFilter.ALL, SeoMarket.DE).forEach(q -> covered.add(q.query().toLowerCase()));
            for (KeywordIdea i : repository.ideas(ideasMonth)) {
                ideas.add(new IdeaRow(i.keyword(), i.seed(), i.searchVolume(), i.cpc(), covered.contains(i.keyword().toLowerCase())));
            }
            ideas.sort(Comparator.comparingInt((IdeaRow i) -> volume(i.volume())).reversed().thenComparing(IdeaRow::keyword));
        }
        SeoCompetition.LastRun lastRun = runs.findTop10ByOrderByStartedAtDesc().stream()
                .filter(r -> SOURCE_SERP.equals(r.getSource()))
                .findFirst()
                .map(r -> new SeoCompetition.LastRun(r.getStartedAt(), r.getStatus(), r.getError()))
                .orElse(null);
        return new View(isEnabled(), week, metricsMonth, round4(cost), deps.properties().getMonthlyBudgetUsd(), lastRun,
                competitors, queries, ideas);
    }

    /** Suchvolumen und CPC des letzten Monats für die Suchbegriff-Tabelle. */
    public List<KeywordRow> keywordMetrics() {
        return repository.latestMetricsMonth()
                .map(m -> repository.metrics(m).stream().map(k -> new KeywordRow(k.query(), k.searchVolume(), k.cpc(), k.competition())).toList())
                .orElse(List.of());
    }

    private static boolean isOwn(String domain, String own) {
        return domain.equals(own) || domain.endsWith("." + own);
    }

    private static int volume(Integer v) {
        return v == null ? -1 : v;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
