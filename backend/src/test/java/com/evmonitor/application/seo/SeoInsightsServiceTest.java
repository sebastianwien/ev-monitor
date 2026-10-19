package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoInsights.BrandFilter;
import com.evmonitor.application.seo.SeoInsights.Overview;
import com.evmonitor.application.seo.SeoInsights.PageFilter;
import com.evmonitor.application.seo.SeoInsights.PageRow;
import com.evmonitor.application.seo.SeoInsights.QueryFilter;
import com.evmonitor.application.seo.SeoInsights.QueryRow;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.PageTotals;
import com.evmonitor.application.seo.SeoInsights.CampaignRow;
import com.evmonitor.application.seo.SeoInsights.ModelFilter;
import com.evmonitor.application.seo.SeoInsights.ModelLevel;
import com.evmonitor.application.seo.SeoInsights.ModelRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.DayRow;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.PageGoalTotals;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.SourceGoalTotals;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.SourceTotals;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoAttributionRepository.DataBasis;
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
        when(attribution.dataBasisByModel()).thenReturn(Map.of());
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
        when(repository.pageDays(from, LATEST)).thenReturn(List.of(
                new GscDailyRepository.PageRow(from, "/modelle/A", 10, 100, 8.0),
                new GscDailyRepository.PageRow(from.plusDays(1), "/modelle/A", 15, 200, 6.0),
                new GscDailyRepository.PageRow(from.plusDays(1), "/", 5, 100, 6.0)));
        when(repository.pageDays(from.minusDays(7), from.minusDays(1))).thenReturn(List.of(
                new GscDailyRepository.PageRow(from.minusDays(3), "/modelle/A", 10, 200, 10.0)));
        when(attribution.registrationTotals(from, LATEST)).thenReturn(new RegistrationTotals(12, 5));
        when(attribution.registrationTotals(from.minusDays(7), from.minusDays(1))).thenReturn(new RegistrationTotals(4, 1));
        when(plausible.dayTotals(from, LATEST)).thenReturn(List.of(
                new DayRow(from, 50, 60, 200, 40, 120), new DayRow(from.plusDays(1), 70, 80, 300, 30, 100)));
        when(plausible.googleVisitsByDay(from, LATEST)).thenReturn(Map.of(from, 12L, from.plusDays(1), 18L));

        Overview o = service.overview(7, SeoMarket.ALL);

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
        when(repository.pageDays(any(), any())).thenReturn(List.of());

        Overview o = service.overview(28, SeoMarket.ALL);

        assertThat(o.to()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(o.current()).isEqualTo(SeoInsights.Totals.EMPTY);
    }

    @Test
    void rejectsUnknownPeriod() {
        assertThatThrownBy(() -> service.overview(10, SeoMarket.ALL)).isInstanceOf(IllegalArgumentException.class);
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

        List<PageRow> all = service.pages(28, PageFilter.ALL, SeoMarket.ALL);

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

        assertThat(service.pages(28, PageFilter.UNDERRATED, SeoMarket.ALL)).extracting(PageRow::path).containsExactly("/modelle/Beta/Two");
        assertThat(service.pages(28, PageFilter.DEAD, SeoMarket.ALL)).extracting(PageRow::path).containsExactly("/modelle/Delta/Four");
        assertThat(service.pages(28, PageFilter.LEAK, SeoMarket.ALL)).extracting(PageRow::path).containsExactly("/modelle/Gamma/Three");
        assertThat(service.pages(28, PageFilter.MONEY, SeoMarket.ALL)).extracting(PageRow::path).containsExactly("/modelle/Alpha/One");
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

        List<QueryRow> nonBrand = service.queries(28, BrandFilter.NON_BRAND, QueryFilter.ALL, SeoMarket.ALL);

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

        assertThat(service.queries(28, BrandFilter.BRAND, QueryFilter.ALL, SeoMarket.ALL)).extracting(QueryRow::query)
                .containsExactly("example monitor login");
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.STRIKING_DISTANCE, SeoMarket.ALL)).extracting(QueryRow::query)
                .containsExactly("beta two reichweite");
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.CANNIBALIZED, SeoMarket.ALL)).extracting(QueryRow::query)
                .containsExactly("alpha one verbrauch");
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.ADWORDS, SeoMarket.ALL)).extracting(QueryRow::query)
                .containsExactly("beta two reichweite");
    }

    private static GscDailyRepository.QueryRow row(String query, String path, int clicks, int impressions,
                                                   double position, boolean brand) {
        return new GscDailyRepository.QueryRow(null, query, path, clicks, impressions, position, brand);
    }

    @Test
    void modelsJoinGscRegistrationsAndDataBasisAndFlagGaps() {
        LocalDate from = LATEST.minusDays(27);
        when(repository.pageTotals(from, LATEST)).thenReturn(List.of(
                new PageTotals("/modelle/Tesla/Model_3", 500, 10_000, 6.0),
                new PageTotals("/modelle/Hyundai/Ioniq_5", 10, 6_000, 25.0),   // Nachfrage, aber keine Daten
                new PageTotals("/modelle/Tesla/Model_Y", 100, 5_000, 12.0),
                new PageTotals("/modelle/Tesla", 50, 2_000, 8.0),              // Markenseite, nur auf Markenebene
                new PageTotals("/modelle/Nirgendwo/Phantom", 1, 50, 40.0),     // unbekanntes Modell: ignoriert
                new PageTotals("/impressum", 5, 100, 30.0)));
        when(attribution.registrationsByLandingPath(from, LATEST))
                .thenReturn(Map.of("/modelle/Tesla/Model_3", 7L, "/modelle/Tesla", 2L));
        when(attribution.dataBasisByModel()).thenReturn(Map.of(
                "MODEL_3", new DataBasis(40, 900),
                "MODEL_Y", new DataBasis(3, 20),
                "ENYAQ", new DataBasis(12, 300)));                               // Daten, aber keine Impressionen

        List<ModelRow> rows = service.models(28, ModelLevel.MODEL, ModelFilter.ALL);

        assertThat(rows).extracting(ModelRow::model).containsExactly("Model 3", "Ioniq 5", "Model Y", "Enyaq");
        ModelRow model3 = rows.get(0);
        assertThat(model3.brand()).isEqualTo("Tesla");
        assertThat(model3.path()).isEqualTo("/modelle/Tesla/Model_3");
        assertThat(model3.impressions()).isEqualTo(10_000);
        assertThat(model3.clicks()).isEqualTo(500);
        assertThat(model3.ctr()).isEqualTo(0.05);
        assertThat(model3.avgPosition()).isEqualTo(6.0);
        assertThat(model3.registrations()).isEqualTo(7);
        assertThat(model3.cars()).isEqualTo(40);
        assertThat(model3.logs()).isEqualTo(900);
        assertThat(model3.finding()).isNull();
        assertThat(rows.get(1).finding()).isEqualTo("CONTENT_GAP");
        assertThat(rows.get(1).cars()).isZero();
        assertThat(rows.get(2).finding()).isNull();
        ModelRow enyaq = rows.get(3);
        assertThat(enyaq.brand()).isEqualTo("Škoda");
        assertThat(enyaq.path()).isEqualTo("/modelle/Škoda/Enyaq");
        assertThat(enyaq.impressions()).isZero();
        assertThat(enyaq.logs()).isEqualTo(300);
        assertThat(enyaq.finding()).isEqualTo("INVISIBLE");

        assertThat(service.models(28, ModelLevel.MODEL, ModelFilter.CONTENT_GAP))
                .extracting(ModelRow::model).containsExactly("Ioniq 5");
        assertThat(service.models(28, ModelLevel.MODEL, ModelFilter.INVISIBLE))
                .extracting(ModelRow::model).containsExactly("Enyaq");
    }

    @Test
    void modelsResolveUnderscoreBrandsAndSumPathVariantsOfOneModel() {
        LocalDate from = LATEST.minusDays(27);
        when(repository.pageTotals(from, LATEST)).thenReturn(List.of(
                new PageTotals("/modelle/Land_Rover/Range_Rover_Electric", 4, 400, 10.0),
                new PageTotals("/modelle/Tesla/Model_Y", 100, 5_000, 12.0),
                new PageTotals("/modelle/Tesla/MODEL_Y", 10, 1_000, 6.0)));   // alte Schreibweise, gleiches Modell
        when(attribution.registrationsByLandingPath(from, LATEST))
                .thenReturn(Map.of("/modelle/Tesla/Model_Y", 3L, "/modelle/Tesla/MODEL_Y", 1L));

        List<ModelRow> rows = service.models(28, ModelLevel.MODEL, ModelFilter.ALL);

        assertThat(rows).extracting(ModelRow::path)
                .containsExactly("/modelle/Tesla/Model_Y", "/modelle/Land_Rover/Range_Rover_Electric");
        ModelRow modelY = rows.get(0);
        assertThat(modelY.impressions()).isEqualTo(6_000);
        assertThat(modelY.clicks()).isEqualTo(110);
        // gewichtet: (12*5000 + 6*1000) / 6000
        assertThat(modelY.avgPosition()).isEqualTo(11.0);
        assertThat(modelY.registrations()).isEqualTo(4);
        assertThat(rows.get(1).brand()).isEqualTo("Land Rover");

        assertThat(service.models(28, ModelLevel.BRAND, ModelFilter.ALL)).extracting(ModelRow::path)
                .containsExactly("/modelle/Tesla", "/modelle/Land_Rover");
    }

    @Test
    void modelsOnBrandLevelSumModelsAndBrandPage() {
        LocalDate from = LATEST.minusDays(27);
        when(repository.pageTotals(from, LATEST)).thenReturn(List.of(
                new PageTotals("/modelle/Tesla/Model_3", 500, 10_000, 6.0),
                new PageTotals("/modelle/Tesla/Model_Y", 100, 5_000, 12.0),
                new PageTotals("/modelle/Tesla", 50, 2_000, 8.0),
                new PageTotals("/modelle/Hyundai/Ioniq_5", 10, 6_000, 25.0)));
        when(attribution.registrationsByLandingPath(from, LATEST))
                .thenReturn(Map.of("/modelle/Tesla/Model_3", 7L, "/modelle/Tesla", 2L));
        when(attribution.dataBasisByModel()).thenReturn(Map.of(
                "MODEL_3", new DataBasis(40, 900), "MODEL_Y", new DataBasis(3, 20), "ENYAQ", new DataBasis(12, 300)));

        List<ModelRow> rows = service.models(28, ModelLevel.BRAND, ModelFilter.ALL);

        assertThat(rows).extracting(ModelRow::brand).containsExactly("Tesla", "Hyundai", "Škoda");
        ModelRow tesla = rows.get(0);
        assertThat(tesla.model()).isNull();
        assertThat(tesla.path()).isEqualTo("/modelle/Tesla");
        assertThat(tesla.impressions()).isEqualTo(17_000);
        assertThat(tesla.clicks()).isEqualTo(650);
        // gewichtet: (6*10000 + 12*5000 + 8*2000) / 17000
        assertThat(tesla.avgPosition()).isEqualTo(8.0);
        assertThat(tesla.registrations()).isEqualTo(9);
        assertThat(tesla.cars()).isEqualTo(43);
        assertThat(tesla.logs()).isEqualTo(920);
        assertThat(tesla.finding()).isNull();
        assertThat(rows.get(2).logs()).isEqualTo(300);
    }

    // --- Markt, Vorzeitraum, Aufklappen, Markenverlauf, Handlungsliste ---

    @Test
    void overviewForGermanyCountsOnlyGermanPathsAndLandingPathRegistrations() {
        LocalDate from = LATEST.minusDays(6);
        when(repository.pageDays(from, LATEST)).thenReturn(List.of(
                new GscDailyRepository.PageRow(from, "/modelle/A", 4, 100, 5.0),
                new GscDailyRepository.PageRow(from, "/en/models/A", 40, 900, 9.0),
                new GscDailyRepository.PageRow(from, "/", 6, 100, 3.0)));
        when(repository.pageDays(from.minusDays(7), from.minusDays(1))).thenReturn(List.of());
        when(attribution.registrationTotals(from, LATEST)).thenReturn(new RegistrationTotals(20, 8));
        when(attribution.landingPathTotals(from, LATEST)).thenReturn(Map.of(
                "/modelle/A", new RegistrationTotals(3, 2), "/en/models/A", new RegistrationTotals(5, 4)));

        Overview de = service.overview(7, SeoMarket.DE);
        assertThat(de.market()).isEqualTo(SeoMarket.DE);
        assertThat(de.current().impressions()).isEqualTo(200);
        assertThat(de.current().clicks()).isEqualTo(10);
        assertThat(de.current().avgPosition()).isEqualTo(4.0);
        assertThat(de.current().registrations()).isEqualTo(3);
        assertThat(de.current().organicRegistrations()).isEqualTo(2);
        assertThat(de.days().get(0).impressions()).isEqualTo(200);

        Overview intl = service.overview(7, SeoMarket.INTL);
        assertThat(intl.current().impressions()).isEqualTo(900);
        assertThat(intl.current().registrations()).isEqualTo(5);

        // Alle Märkte: Registrierungen ohne bekannte erste Seite zählen mit
        assertThat(service.overview(7, SeoMarket.ALL).current().registrations()).isEqualTo(20);
    }

    @Test
    void previousWindowIsOnlyComparableWhenDataReachesBackFarEnough() {
        when(repository.pageDays(any(), any())).thenReturn(List.of());
        LocalDate from = LATEST.minusDays(6);

        when(repository.earliestDay()).thenReturn(Optional.of(from.minusDays(7)));
        assertThat(service.overview(7, SeoMarket.ALL).previousComplete()).isTrue();

        when(repository.earliestDay()).thenReturn(Optional.of(from.minusDays(3)));
        assertThat(service.overview(7, SeoMarket.ALL).previousComplete()).isFalse();

        when(repository.earliestDay()).thenReturn(Optional.empty());
        assertThat(service.overview(7, SeoMarket.ALL).previousComplete()).isFalse();
    }

    @Test
    void marketFilterAppliesToPagesAndQueries() {
        when(repository.pageTotals(any(), any())).thenReturn(List.of(
                new PageTotals("/modelle/A", 10, 1000, 5.0), new PageTotals("/se/modeller/A", 50, 5000, 6.0)));
        when(repository.pathsWithImpressions(any(), any())).thenReturn(List.of());
        when(repository.queryPathTotals(any(), any())).thenReturn(List.of(
                new GscDailyRepository.QueryRow(null, "testauto reichweite", "/modelle/A", 3, 300, 7.0, false),
                new GscDailyRepository.QueryRow(null, "testauto reichweite", "/se/modeller/A", 9, 900, 4.0, false),
                new GscDailyRepository.QueryRow(null, "testauto räckvidd", "/se/modeller/A", 20, 2000, 3.0, false)));

        assertThat(service.pages(28, PageFilter.ALL, SeoMarket.DE)).extracting(PageRow::path).containsExactly("/modelle/A");
        assertThat(service.pages(28, PageFilter.ALL, SeoMarket.INTL)).extracting(PageRow::path).containsExactly("/se/modeller/A");

        List<QueryRow> de = service.queries(28, BrandFilter.ALL, QueryFilter.ALL, SeoMarket.DE);
        assertThat(de).extracting(QueryRow::query).containsExactly("testauto reichweite");
        assertThat(de.get(0).impressions()).isEqualTo(300);
        assertThat(de.get(0).pageCount()).isEqualTo(1);
        assertThat(service.queries(28, BrandFilter.ALL, QueryFilter.ALL, SeoMarket.ALL)).hasSize(2);
    }

    @Test
    void pageQueriesAndQueryPagesBreakDownOneRowOfTheOtherTable() {
        when(repository.queryPathTotals(any(), any())).thenReturn(List.of(
                new GscDailyRepository.QueryRow(null, "testauto reichweite", "/modelle/A", 3, 300, 7.0, false),
                new GscDailyRepository.QueryRow(null, "testauto verbrauch", "/modelle/A", 8, 500, 4.0, false),
                new GscDailyRepository.QueryRow(null, "testauto reichweite", "/modelle/B", 1, 50, 12.0, false),
                new GscDailyRepository.QueryRow(null, "ev monitor", "/", 9, 90, 1.0, true)));

        List<SeoInsights.Breakdown> queries = service.pageQueries(28, "/modelle/A");
        assertThat(queries).extracting(SeoInsights.Breakdown::key).containsExactly("testauto verbrauch", "testauto reichweite");
        assertThat(queries.get(0).ctr()).isEqualTo(0.016);

        List<SeoInsights.Breakdown> pages = service.queryPages(28, "testauto reichweite");
        assertThat(pages).extracting(SeoInsights.Breakdown::key).containsExactly("/modelle/A", "/modelle/B");
        assertThat(pages.get(1).avgPosition()).isEqualTo(12.0);
    }

    @Test
    void brandTrendSumsBrandAndNonBrandPerIsoWeek() {
        // 16.10.2026 ist ein Freitag, die Woche beginnt am Montag 12.10.
        when(repository.brandDayTotals(any(), any())).thenReturn(List.of(
                new GscDailyRepository.BrandDay(LocalDate.of(2026, 10, 12), true, 10, 2),
                new GscDailyRepository.BrandDay(LocalDate.of(2026, 10, 16), true, 5, 1),
                new GscDailyRepository.BrandDay(LocalDate.of(2026, 10, 16), false, 100, 4),
                new GscDailyRepository.BrandDay(LocalDate.of(2026, 10, 9), true, 7, 0)));

        List<SeoInsights.BrandWeek> weeks = service.brandTrend(2);

        assertThat(weeks).hasSize(2);
        assertThat(weeks.get(0).weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(weeks.get(0).brandImpressions()).isEqualTo(7);
        assertThat(weeks.get(1).weekStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(weeks.get(1).brandImpressions()).isEqualTo(15);
        assertThat(weeks.get(1).brandClicks()).isEqualTo(3);
        assertThat(weeks.get(1).nonBrandImpressions()).isEqualTo(100);
    }

    @Test
    void opportunitiesCountFindingsOfPagesQueriesAndModels() {
        when(repository.pageTotals(any(), any())).thenReturn(List.of());
        when(repository.pathsWithImpressions(any(), any())).thenReturn(List.of("/modelle/Weg"));
        when(repository.queryPathTotals(any(), any())).thenReturn(List.of());

        SeoInsights.Opportunities o = service.opportunities(28, SeoMarket.DE);

        assertThat(o.dead()).isEqualTo(1);
        assertThat(o.underrated()).isZero();
        assertThat(o.adwords()).isZero();
    }

    @Test
    void brandQueryOnSeveralPagesIsNotCannibalized() {
        // Bei Markensuchen zeigt Google mehrere eigene Seiten (Sitelinks), das ist keine Konkurrenz im eigenen Haus
        when(repository.queryPathTotals(any(), any())).thenReturn(List.of(
                new GscDailyRepository.QueryRow(null, "ev monitor", "/", 30, 300, 1.0, true),
                new GscDailyRepository.QueryRow(null, "ev monitor", "/modelle", 5, 200, 2.0, true),
                new GscDailyRepository.QueryRow(null, "testauto reichweite", "/modelle/A", 3, 300, 7.0, false),
                new GscDailyRepository.QueryRow(null, "testauto reichweite", "/modelle/B", 1, 200, 9.0, false)));

        List<QueryRow> rows = service.queries(28, BrandFilter.ALL, QueryFilter.CANNIBALIZED, SeoMarket.ALL);

        assertThat(rows).extracting(QueryRow::query).containsExactly("testauto reichweite");
    }
}
