package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoCompetition.CompetitorRow;
import com.evmonitor.application.seo.SeoCompetition.IdeaRow;
import com.evmonitor.application.seo.SeoCompetition.SerpQueryRow;
import com.evmonitor.application.seo.SeoCompetition.View;
import com.evmonitor.application.seo.SeoInsights.BrandFilter;
import com.evmonitor.application.seo.SeoInsights.QueryFilter;
import com.evmonitor.application.seo.SeoInsights.QueryRow;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.KeywordData;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.Paid;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoClient.SerpItem;
import com.evmonitor.infrastructure.external.dataforseo.DataForSeoException;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.KeywordIdea;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.KeywordMetric;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.SerpRow;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunEntity;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Erfundene Begriffe und Domains; Client, Repositories und Insights gemockt. */
class SeoCompetitionServiceTest {

    /** Mittwoch 21.10.2026: Woche ab 19.10., Monat ab 01.10. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T03:30:00Z"), ZoneId.of("Europe/Berlin"));
    private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);
    private static final LocalDate MONTH = LocalDate.of(2026, 10, 1);

    private final DataForSeoClient client = mock(DataForSeoClient.class);
    private final SeoCompetitionRepository repository = mock(SeoCompetitionRepository.class);
    private final SeoInsightsService insights = mock(SeoInsightsService.class);
    private final SeoSyncRunRepository runs = mock(SeoSyncRunRepository.class);
    private final SeoCompetitionProperties properties = new SeoCompetitionProperties();
    private SeoCompetitionService service;

    private static QueryRow q(String query, long impressions, boolean brand) {
        return new QueryRow(query, brand, impressions, 1, 0.01, 8.0, "/modelle/A", 1, false, null);
    }

    @BeforeEach
    void setUp() {
        properties.setLogin("user");
        properties.setPassword("pw");
        properties.setTopQueries(2);
        properties.setSuggestionSeeds(1);
        properties.setSuggestionLimit(5);
        properties.setOwnDomain("example.test");
        service = new SeoCompetitionService(client, repository, insights, runs, new SeoCompetitionService.Deps(properties, CLOCK));
        when(client.isEnabled()).thenReturn(true);
        when(repository.latestSerpWeek()).thenReturn(Optional.of(WEEK));
        when(repository.latestMetricsMonth()).thenReturn(Optional.of(MONTH));
        when(insights.queries(28, BrandFilter.NON_BRAND, QueryFilter.ALL, SeoMarket.DE)).thenReturn(List.of(
                q("testauto reichweite", 900, false), q("testauto verbrauch", 500, false), q("testauto winter", 100, false)));
        when(insights.queries(28, BrandFilter.ALL, QueryFilter.ALL, SeoMarket.DE)).thenReturn(List.of(
                q("testauto reichweite", 900, false), q("ev monitor", 300, true), q("a ".repeat(41).trim(), 20, false)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void weeklySerpForTopNonBrandGermanQueriesWithCostOnTheRun() {
        when(repository.latestSerpWeek()).thenReturn(Optional.of(WEEK.minusWeeks(1)));
        when(client.serp("testauto reichweite")).thenReturn(new Paid<>(List.of(
                new SerpItem(1, "wettbewerb.test", "https://wettbewerb.test/a", "A"),
                new SerpItem(2, "example.test", "https://example.test/modelle/A", "Wir")), 0.002));
        when(client.serp("testauto verbrauch")).thenReturn(new Paid<>(List.of(), 0.002));

        SeoCompetitionService.RunResult result = service.run();

        assertThat(result.serpQueries()).isEqualTo(2);
        assertThat(result.costUsd()).isEqualTo(0.004);
        verify(client, never()).serp("testauto winter");
        ArgumentCaptor<List<SerpRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceSerpWeek(eq(WEEK), rows.capture());
        assertThat(rows.getValue()).extracting(SerpRow::domain).containsExactly("wettbewerb.test", "example.test");
        ArgumentCaptor<SeoSyncRunEntity> run = ArgumentCaptor.forClass(SeoSyncRunEntity.class);
        verify(runs, times(2)).save(run.capture());
        assertThat(run.getValue().getSource()).isEqualTo(SeoCompetitionService.SOURCE_SERP);
        assertThat(run.getValue().getStatus()).isEqualTo(SeoSyncRunEntity.STATUS_SUCCESS);
        assertThat(run.getValue().getCostUsd().doubleValue()).isEqualTo(0.004);
        verify(client, never()).searchVolume(anyList());
    }

    @Test
    void stopsBeforeTheMonthlyBudgetIsExceeded() {
        when(repository.latestSerpWeek()).thenReturn(Optional.empty());
        properties.setMonthlyBudgetUsd(1.0);
        when(repository.costSince(eq(SeoCompetitionService.SOURCE_SERP), any())).thenReturn(0.999);

        SeoCompetitionService.RunResult result = service.run();

        verify(client, never()).serp(anyString());
        assertThat(result.status()).isEqualTo(SeoSyncRunEntity.STATUS_PARTIAL);
        assertThat(result.message()).contains("Monatsbudget");
    }

    @Test
    @SuppressWarnings("unchecked")
    void monthlyVolumesForValidGermanQueriesAndIdeasFromTopSeeds() {
        when(repository.latestMetricsMonth()).thenReturn(Optional.of(MONTH.minusMonths(1)));
        when(client.searchVolume(anyList())).thenReturn(new Paid<>(List.of(
                new KeywordData("testauto reichweite", 1300, 0.84, "LOW"), new KeywordData("ev monitor", 90, null, null)), 0.075));
        when(client.suggestions("testauto reichweite", 5)).thenReturn(new Paid<>(List.of(
                new KeywordData("testauto reichweite winter", 210, 1.2, "MEDIUM")), 0.0126));

        service.run();

        ArgumentCaptor<List<String>> sent = ArgumentCaptor.forClass(List.class);
        verify(client).searchVolume(sent.capture());
        // über 10 Wörter lehnt Google Ads ab, solche Begriffe werden nicht geschickt
        assertThat(sent.getValue()).containsExactly("testauto reichweite", "ev monitor");
        ArgumentCaptor<List<KeywordMetric>> metrics = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceMetricsMonth(eq(MONTH), metrics.capture());
        assertThat(metrics.getValue()).extracting(KeywordMetric::query).contains("testauto reichweite", "testauto reichweite winter");
        ArgumentCaptor<List<KeywordIdea>> ideas = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceIdeasMonth(eq(MONTH), ideas.capture());
        assertThat(ideas.getValue()).containsExactly(new KeywordIdea("testauto reichweite winter", "testauto reichweite", 210, 1.2));
    }

    @Test
    void nothingDueMeansNoPaidCallAndNoRun() {
        SeoCompetitionService.RunResult result = service.run();

        assertThat(result.status()).isEqualTo(SeoCompetitionService.UP_TO_DATE);
        verify(client, never()).serp(anyString());
        verify(client, never()).searchVolume(anyList());
        verify(client, never()).suggestions(anyString(), anyInt());
        verify(runs, never()).save(any());
    }

    @Test
    void repeatedApiErrorsAbortTheSerpLoop() {
        when(repository.latestSerpWeek()).thenReturn(Optional.empty());
        properties.setTopQueries(10);
        when(insights.queries(28, BrandFilter.NON_BRAND, QueryFilter.ALL, SeoMarket.DE)).thenReturn(List.of(
                q("a1", 9, false), q("a2", 8, false), q("a3", 7, false), q("a4", 6, false), q("a5", 5, false)));
        when(client.serp(anyString())).thenThrow(new DataForSeoException("/serp: 40100 Unauthorized."));

        SeoCompetitionService.RunResult result = service.run();

        verify(client, times(3)).serp(anyString());
        assertThat(result.status()).isEqualTo(SeoSyncRunEntity.STATUS_FAILED);
        assertThat(result.message()).contains("40100");
        verify(repository, never()).replaceSerpWeek(any(), anyList());
    }

    @Test
    void viewAggregatesCompetitorsOurRankAndIdeaCoverage() {
        when(repository.serpRows(WEEK)).thenReturn(List.of(
                new SerpRow("testauto reichweite", 1, "wettbewerb.test", "https://wettbewerb.test/a", "A"),
                new SerpRow("testauto reichweite", 2, "example.test", "https://example.test/modelle/A", "Wir"),
                new SerpRow("testauto reichweite", 3, "forum.test", "https://forum.test/t", "F"),
                new SerpRow("testauto verbrauch", 1, "wettbewerb.test", "https://wettbewerb.test/b", "B"),
                new SerpRow("testauto verbrauch", 2, "forum.test", "https://forum.test/u", "U")));
        when(repository.metrics(MONTH)).thenReturn(List.of(new KeywordMetric("testauto reichweite", 1300, 0.84, "LOW")));
        when(repository.latestIdeasMonth()).thenReturn(Optional.of(MONTH));
        when(repository.ideas(MONTH)).thenReturn(List.of(
                new KeywordIdea("testauto reichweite", "testauto reichweite", 1300, 0.84),
                new KeywordIdea("testauto ladezeit", "testauto reichweite", 400, 0.5)));
        when(insights.queries(28, BrandFilter.ALL, QueryFilter.ALL, SeoMarket.DE)).thenReturn(List.of(q("testauto reichweite", 900, false)));
        when(insights.queries(90, BrandFilter.ALL, QueryFilter.ALL, SeoMarket.DE)).thenReturn(List.of(q("testauto reichweite", 2000, false)));

        View view = service.view(28);

        Map<String, CompetitorRow> competitors = view.competitors().stream().collect(Collectors.toMap(CompetitorRow::domain, c -> c));
        assertThat(competitors).doesNotContainKey("example.test");
        assertThat(competitors.get("wettbewerb.test").queries()).isEqualTo(2);
        assertThat(competitors.get("wettbewerb.test").aheadOfUs()).isEqualTo(2);
        assertThat(competitors.get("forum.test").aheadOfUs()).isEqualTo(1);
        assertThat(view.competitors().get(0).domain()).isEqualTo("wettbewerb.test");

        SerpQueryRow first = view.queries().stream().filter(r -> r.query().equals("testauto reichweite")).findFirst().orElseThrow();
        assertThat(first.ourRank()).isEqualTo(2);
        assertThat(first.volume()).isEqualTo(1300);
        assertThat(first.impressions()).isEqualTo(900);
        assertThat(first.top()).hasSize(3);
        assertThat(view.queries().stream().filter(r -> r.query().equals("testauto verbrauch")).findFirst().orElseThrow().ourRank()).isNull();

        assertThat(view.ideas()).extracting(IdeaRow::keyword, IdeaRow::covered)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("testauto reichweite", true),
                        org.assertj.core.groups.Tuple.tuple("testauto ladezeit", false));
    }
}
