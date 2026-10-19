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
import com.evmonitor.application.seo.SeoInsights.CampaignRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.DayRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.PageGoalTotals;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.SourceGoalTotals;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.SourceTotals;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository.RegistrationTotals;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository.UtmTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
    private final SeoAttributionRepository attribution = mock(SeoAttributionRepository.class);
    private final PlausibleDailyRepository plausible = mock(PlausibleDailyRepository.class);
    private final SeoInsightsService service = new SeoInsightsService(repository, attribution, plausible, new SeoScoringProperties(), CLOCK);

    @BeforeEach
    void setUp() {
        when(repository.latestDay()).thenReturn(Optional.of(LATEST));
        when(attribution.registrationTotals(any(), any())).thenReturn(RegistrationTotals.EMPTY);
        when(attribution.registrationsByLandingPath(any(), any())).thenReturn(Map.of());
        when(attribution.registrationsByUtm(any(), any())).thenReturn(Map.of());
        when(plausible.dayTotals(any(), any())).thenReturn(List.of());
        when(plausible.googleVisitsByDay(any(), any())).thenReturn(Map.of());
        when(plausible.pageTotals(any(), any())).thenReturn(List.of());
        when(plausible.pageGoalTotals(any(), any())).thenReturn(List.of());
        when(plausible.sourceTotals(any(), any())).thenReturn(List.of());
        when(plausible.sourceGoalTotals(any(), any())).thenReturn(List.of());
    }

    @Test
    void overviewEndsAtLatestSyncedDayAndComparesWithPreviousWindow() {
        LocalDate from = LATEST.minusDays(6);
        when(repository.dayTotals(from, LATEST)).thenReturn(List.of(
                new DayTotals(from, 10, 100, 8.0),
                new DayTotals(from.plusDays(1), 20, 300, 6.0)));
        when(repository.dayTotals(from.minusDays(7), from.minusDays(1))).thenReturn(List.of(
                new DayTotals(from.minusDays(3), 10, 200, 10.0)));
        when(attribution.registrationTotals(from, LATEST)).thenReturn(new RegistrationTotals(12, 5));
        when(attribution.registrationTotals(from.minusDays(7), from.minusDays(1))).thenReturn(new RegistrationTotals(4, 1));
        when(plausible.dayTotals(from, LATEST)).thenReturn(List.of(
                new DayRow(from, 50, 60, 200, 40, 120), new DayRow(from.plusDays(1), 70, 80, 300, 30, 100)));
        when(plausible.googleVisitsByDay(from, LATEST)).thenReturn(Map.of(from, 12L, from.plusDays(1), 18L));

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
        assertThat(o.current().registrations()).isEqualTo(12);
        assertThat(o.current().organicRegistrations()).isEqualTo(5);
        assertThat(o.previous().registrations()).isEqualTo(4);
        assertThat(o.current().visitors()).isEqualTo(120);
        assertThat(o.current().googleEntries()).isEqualTo(30);
        assertThat(o.days().get(1).visitors()).isEqualTo(70);
        assertThat(o.days().get(1).googleEntries()).isEqualTo(18);
        assertThat(o.days().get(2).visitors()).isZero();
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
                new PageTotals("/modelle/Gamma/Three", 90, 500, 2.0),     // Klicks über Median (85), Position 2
                new PageTotals("/impressum", 5, 100, 30.0)));
        when(repository.pathsWithImpressions(from.minusDays(90), from.minusDays(1)))
                .thenReturn(List.of("/modelle/Alpha/One", "/modelle/Delta/Four"));
        when(attribution.registrationsByLandingPath(from, LATEST))
                .thenReturn(Map.of("/modelle/Alpha/One", 7L, "/forum-landing", 2L));
        when(plausible.pageTotals(from, LATEST)).thenReturn(List.of(
                new PlausibleDailyRepository.PageTotals("/modelle/Alpha/One", 600, 900, 480, 20.0, 150),
                new PlausibleDailyRepository.PageTotals("/modelle/Gamma/Three", 25, 30, 20, 85.0, 12),   // Leck: Absprung 85 %, 0 CTA
                new PlausibleDailyRepository.PageTotals("/only-plausible", 9, 9, 9, 10.0, 60)));
        when(plausible.pageGoalTotals(from, LATEST)).thenReturn(List.of(
                new PageGoalTotals("/modelle/Alpha/One", "gate_code_verified", 24),
                new PageGoalTotals("/modelle/Alpha/One", "cta_register_clicked", 24),
                new PageGoalTotals("/modelle/Alpha/One", "dashboard_viewed", 999),       // kein CTA-Goal
                new PageGoalTotals("/only-plausible", "registration_completed", 3)));

        List<PageRow> all = service.pages(28, PageFilter.ALL);

        assertThat(all).extracting(PageRow::path)
                .containsExactly("/modelle/Alpha/One", "/modelle/Beta/Two", "/modelle/Gamma/Three", "/impressum",
                        "/forum-landing", "/modelle/Delta/Four", "/only-plausible");
        assertThat(all.get(0).registrations()).isEqualTo(7);
        // Join: Einstiege, Absprung als Anteil, CTA-Rate = (24 + 24) / 480 nur aus CTA-Goals
        assertThat(all.get(0).entries()).isEqualTo(480);
        assertThat(all.get(0).bounceRate()).isEqualTo(0.2);
        assertThat(all.get(0).ctaRate()).isEqualTo(0.1);
        // Geldseite: einzige Seite mit Registrierungen und Klicks
        assertThat(all.get(0).finding()).isEqualTo("MONEY");
        assertThat(all.get(6).entries()).isEqualTo(9);
        assertThat(all.get(6).ctaRate()).isEqualTo(0.3333);
        assertThat(all.get(1).registrations()).isZero();
        // Landing-Seite ohne Google-Sichtbarkeit bleibt sichtbar, Registrierungen vor toten Seiten
        assertThat(all.get(4).registrations()).isEqualTo(2);
        assertThat(all.get(4).impressions()).isZero();
        assertThat(all.get(4).finding()).isNull();
        assertThat(all.get(1).finding()).isEqualTo("UNDERRATED");
        assertThat(all.get(1).ctr()).isEqualTo(0.01);
        // Leck: Klicks 90 über Median (85), Absprung 85 % über 70 %, CTA-Rate 0 unter Median (0.1)
        assertThat(all.get(2).finding()).isEqualTo("LEAK");
        assertThat(all.get(5).finding()).isEqualTo("DEAD");
        assertThat(all.get(5).impressions()).isZero();

        assertThat(service.pages(28, PageFilter.UNDERRATED)).extracting(PageRow::path).containsExactly("/modelle/Beta/Two");
        assertThat(service.pages(28, PageFilter.DEAD)).extracting(PageRow::path).containsExactly("/modelle/Delta/Four");
        assertThat(service.pages(28, PageFilter.LEAK)).extracting(PageRow::path).containsExactly("/modelle/Gamma/Three");
        assertThat(service.pages(28, PageFilter.MONEY)).extracting(PageRow::path).containsExactly("/modelle/Alpha/One");
    }

    @Test
    void campaignsJoinDbByUtmAndGoogleOrganicByReferrer() {
        LocalDate from = LATEST.minusDays(27);
        when(plausible.sourceTotals(from, LATEST)).thenReturn(List.of(
                new SourceTotals("Google", "", "", 500, 560),
                new SourceTotals("vw-forum", "forum", "Vorstellung", 40, 44),
                new SourceTotals("Direct / None", "", "", 300, 320)));
        when(plausible.sourceGoalTotals(from, LATEST)).thenReturn(List.of(
                new SourceGoalTotals("vw-forum", "forum", "Vorstellung", "gate_code_verified", 4),
                new SourceGoalTotals("vw-forum", "forum", "Vorstellung", "dashboard_viewed", 30),
                new SourceGoalTotals("Google", "", "", "cta_register_clicked", 25)));
        when(attribution.registrationsByUtm(from, LATEST)).thenReturn(Map.of("vw-forum|forum|vorstellung", new UtmTotals(3, 1)));
        when(attribution.registrationTotals(from, LATEST)).thenReturn(new RegistrationTotals(40, 11));

        List<CampaignRow> rows = service.campaigns(28);

        assertThat(rows).extracting(CampaignRow::source).containsExactly("Google", "Direct / None", "vw-forum");
        CampaignRow google = rows.get(0);
        assertThat(google.ctaRate()).isEqualTo(0.05);
        assertThat(google.registrations()).isEqualTo(11);
        assertThat(google.attributed()).isTrue();
        CampaignRow direct = rows.get(1);
        assertThat(direct.registrations()).isZero();
        assertThat(direct.attributed()).isFalse();
        CampaignRow forum = rows.get(2);
        assertThat(forum.ctaVisitors()).isEqualTo(4);
        assertThat(forum.ctaRate()).isEqualTo(0.1);
        assertThat(forum.registrations()).isEqualTo(3);
        assertThat(forum.trials()).isEqualTo(1);
        assertThat(forum.attributed()).isTrue();
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
        // Beta/Two konvertiert organisch gut (CTA 20 % über Median), Position 14 > 10: AdWords-Kandidat
        when(plausible.pageTotals(from, LATEST)).thenReturn(List.of(
                new PlausibleDailyRepository.PageTotals("/modelle/Beta/Two", 10, 12, 10, 40.0, 90),
                new PlausibleDailyRepository.PageTotals("/modelle/Alpha/One", 400, 500, 380, 30.0, 120)));
        when(plausible.pageGoalTotals(from, LATEST)).thenReturn(List.of(
                new PageGoalTotals("/modelle/Beta/Two", "gate_code_verified", 2),
                new PageGoalTotals("/modelle/Alpha/One", "gate_code_verified", 19)));

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
        assertThat(nonBrand.get(1).adwordsCandidate()).isTrue();
        assertThat(nonBrand.get(0).adwordsCandidate()).isFalse();   // Position 7.83 unter 10
        assertThat(nonBrand.get(2).finding()).isNull();

        assertThat(service.queries(28, BrandFilter.BRAND, QueryFilter.ALL)).extracting(QueryRow::query)
                .containsExactly("example monitor login");
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.STRIKING_DISTANCE)).extracting(QueryRow::query)
                .containsExactly("beta two reichweite");
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.CANNIBALIZED)).extracting(QueryRow::query)
                .containsExactly("alpha one verbrauch");
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.ADWORDS)).extracting(QueryRow::query)
                .containsExactly("beta two reichweite");
    }

    private static GscDailyRepository.QueryRow row(String query, String path, int clicks, int impressions,
                                                   double position, boolean brand) {
        return new GscDailyRepository.QueryRow(null, query, path, clicks, impressions, position, brand);
    }
}
