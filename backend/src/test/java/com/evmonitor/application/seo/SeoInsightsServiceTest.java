package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoInsights.BrandFilter;
import com.evmonitor.application.seo.SeoInsights.Overview;
import com.evmonitor.application.seo.SeoInsights.PageFilter;
import com.evmonitor.application.seo.SeoInsights.PageRow;
import com.evmonitor.application.seo.SeoInsights.QueryFilter;
import com.evmonitor.application.seo.SeoInsights.QueryRow;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.DayTotals;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.PageTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Erfundene Pfade und Suchbegriffe, keine Exporte aus der Search Console. */
class SeoInsightsServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final LocalDate LATEST = LocalDate.of(2026, 10, 16);

    private final GscDailyRepository repository = mock(GscDailyRepository.class);
    private final SeoInsightsService service = new SeoInsightsService(repository, new SeoScoringProperties(), CLOCK);

    @BeforeEach
    void setUp() {
        when(repository.latestDay()).thenReturn(Optional.of(LATEST));
    }

    @Test
    void overviewEndsAtLatestSyncedDayAndComparesWithPreviousWindow() {
        LocalDate from = LATEST.minusDays(6);
        when(repository.dayTotals(from, LATEST)).thenReturn(List.of(
                new DayTotals(from, 10, 100, 8.0),
                new DayTotals(from.plusDays(1), 20, 300, 6.0)));
        when(repository.dayTotals(from.minusDays(7), from.minusDays(1))).thenReturn(List.of(
                new DayTotals(from.minusDays(3), 10, 200, 10.0)));

        Overview o = service.overview(7);

        assertThat(o.from()).isEqualTo(from);
        assertThat(o.to()).isEqualTo(LATEST);
        assertThat(o.current().impressions()).isEqualTo(400);
        assertThat(o.current().clicks()).isEqualTo(30);
        assertThat(o.current().ctr()).isEqualTo(0.075);
        // gewichtet: (8*100 + 6*300) / 400 = 6.5
        assertThat(o.current().avgPosition()).isEqualTo(6.5);
        assertThat(o.previous().impressions()).isEqualTo(200);
        assertThat(o.previous().avgPosition()).isEqualTo(10.0);
        // Tagesreihe lückenlos über das Fenster, fehlende Tage mit 0
        assertThat(o.days()).hasSize(7);
        assertThat(o.days().get(0).impressions()).isEqualTo(100);
        assertThat(o.days().get(2).impressions()).isZero();
    }

    @Test
    void overviewWithoutDataFallsBackToTwoDaysAgo() {
        when(repository.latestDay()).thenReturn(Optional.empty());
        when(repository.dayTotals(any(), any())).thenReturn(List.of());

        Overview o = service.overview(28);

        assertThat(o.to()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(o.current()).isEqualTo(SeoInsights.Totals.EMPTY);
    }

    @Test
    void rejectsUnknownPeriod() {
        assertThatThrownBy(() -> service.overview(10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pagesFlagUnderratedAndDeadAndSortByImpressions() {
        LocalDate from = LATEST.minusDays(27);
        when(repository.pageTotals(from, LATEST)).thenReturn(List.of(
                new PageTotals("/modelle/Alpha/One", 500, 10_000, 6.0),   // CTR 5 %, über Median
                new PageTotals("/modelle/Beta/Two", 80, 8_000, 9.0),      // CTR 1 %, Position 9: unterschätzt
                new PageTotals("/modelle/Gamma/Three", 20, 500, 2.0),     // zu wenig Impressionen, Position 2
                new PageTotals("/impressum", 5, 100, 30.0)));
        when(repository.pathsWithImpressions(from.minusDays(90), from.minusDays(1)))
                .thenReturn(List.of("/modelle/Alpha/One", "/modelle/Delta/Four"));

        List<PageRow> all = service.pages(28, PageFilter.ALL);

        assertThat(all).extracting(PageRow::path)
                .containsExactly("/modelle/Alpha/One", "/modelle/Beta/Two", "/modelle/Gamma/Three", "/impressum", "/modelle/Delta/Four");
        assertThat(all.get(1).finding()).isEqualTo("UNDERRATED");
        assertThat(all.get(1).ctr()).isEqualTo(0.01);
        assertThat(all.get(0).finding()).isNull();
        assertThat(all.get(4).finding()).isEqualTo("DEAD");
        assertThat(all.get(4).impressions()).isZero();

        assertThat(service.pages(28, PageFilter.UNDERRATED)).extracting(PageRow::path).containsExactly("/modelle/Beta/Two");
        assertThat(service.pages(28, PageFilter.DEAD)).extracting(PageRow::path).containsExactly("/modelle/Delta/Four");
    }

    @Test
    void queriesAggregateAcrossPagesWithBrandAndFindings() {
        LocalDate from = LATEST.minusDays(27);
        when(repository.queryPathTotals(from, LATEST)).thenReturn(List.of(
                row("alpha one verbrauch", "/modelle/Alpha/One", 400, 5000, 7.0, false),
                row("alpha one verbrauch", "/en/models/Alpha/One", 50, 1000, 12.0, false),
                row("beta two reichweite", "/modelle/Beta/Two", 10, 3000, 14.0, false),
                row("gamma three", "/modelle/Gamma/Three", 90, 100, 1.5, false),
                row("example monitor login", "/login", 300, 320, 1.0, true),
                row("longtail rauschen", "/modelle", 0, 3, 12.0, false),
                row("longtail rauschen", "/en/models", 0, 2, 15.0, false)));

        List<QueryRow> nonBrand = service.queries(28, BrandFilter.NON_BRAND, QueryFilter.ALL);

        assertThat(nonBrand).extracting(QueryRow::query)
                .containsExactly("alpha one verbrauch", "beta two reichweite", "gamma three", "longtail rauschen");
        // unter minImpressions kein Befund, auch nicht Kannibalisierung
        assertThat(nonBrand.get(3).finding()).isNull();
        QueryRow alpha = nonBrand.get(0);
        assertThat(alpha.impressions()).isEqualTo(6000);
        assertThat(alpha.clicks()).isEqualTo(450);
        assertThat(alpha.topPath()).isEqualTo("/modelle/Alpha/One");
        assertThat(alpha.pageCount()).isEqualTo(2);
        // gewichtet: (7*5000 + 12*1000) / 6000 = 7.83
        assertThat(alpha.avgPosition()).isEqualTo(7.83);
        assertThat(alpha.finding()).isEqualTo("CANNIBALIZED");
        assertThat(nonBrand.get(1).finding()).isEqualTo("STRIKING_DISTANCE");
        assertThat(nonBrand.get(2).finding()).isNull();

        assertThat(service.queries(28, BrandFilter.BRAND, QueryFilter.ALL)).extracting(QueryRow::query)
                .containsExactly("example monitor login");
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.STRIKING_DISTANCE)).extracting(QueryRow::query)
                .containsExactly("beta two reichweite");
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.CANNIBALIZED)).extracting(QueryRow::query)
                .containsExactly("alpha one verbrauch");
    }

    private static GscDailyRepository.QueryRow row(String query, String path, int clicks, int impressions,
                                                   double position, boolean brand) {
        return new GscDailyRepository.QueryRow(null, query, path, clicks, impressions, position, brand);
    }
}
