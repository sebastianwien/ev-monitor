package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoAudit.AuditRow;
import com.evmonitor.application.seo.SeoAudit.Check;
import com.evmonitor.application.seo.SeoAudit.Finding;
import com.evmonitor.application.seo.SeoAudit.View;
import com.evmonitor.infrastructure.external.audit.SiteCrawler;
import com.evmonitor.infrastructure.external.audit.SiteCrawler.Fetched;
import com.evmonitor.infrastructure.external.audit.SiteCrawlerException;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunEntity;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Crawl gegen H2 mit gemocktem Crawler: erfundene Domain, erfundene Seiten. */
class SeoAuditServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    private SiteCrawler crawler;

    @Autowired
    private SeoAuditService service;
    @Autowired
    private SeoAuditProperties properties;
    @Autowired
    private SeoSyncRunRepository runs;
    @Autowired
    private JdbcTemplate jdbc;

    private String originalBase;
    private String originalHost;
    private long originalDelay;
    private int originalMaxExtra;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM seo_page_audit");
        jdbc.update("DELETE FROM seo_sync_run");
        originalBase = properties.getBaseUrl();
        originalHost = properties.getSiteHost();
        originalDelay = properties.getDelayMs();
        originalMaxExtra = properties.getMaxExtraUrls();
        properties.setBaseUrl("https://example.test");
        properties.setSiteHost("example.test");
        properties.setDelayMs(0);
    }

    @AfterEach
    void restore() {
        properties.setBaseUrl(originalBase);
        properties.setSiteHost(originalHost);
        properties.setDelayMs(originalDelay);
        properties.setMaxExtraUrls(originalMaxExtra);
    }

    private static String html(String path, String body) {
        return "<html><head><title>Eine Testseite mit genug Zeichen im Title</title>"
                + "<meta name=\"description\" content=\"Eine erfundene Beschreibung, die lang genug ist, damit der Längencheck hier nicht anschlägt.\">"
                + "<link rel=\"canonical\" href=\"https://example.test" + path + "\">"
                + "<script type=\"application/ld+json\">{\"@type\":\"ItemList\"}</script>"
                + "</head><body><h1>Seite</h1>" + body + "</body></html>";
    }

    private void site() {
        when(crawler.sitemapPaths()).thenReturn(List.of("/", "/modelle", "/blog", "/verwaist"));
        when(crawler.fetch("/")).thenReturn(new Fetched(200, html("/", "<a href=\"/modelle\">m</a><a href=\"/blog\">b</a>")));
        when(crawler.fetch("/modelle")).thenReturn(new Fetched(200, html("/modelle",
                "<a href=\"/blog\">b</a><a href=\"/kaputt\">k</a><a href=\"/en/models\">en</a><a href=\"/login\">l</a><a href=\"/duenn\">d</a>")));
        when(crawler.fetch("/blog")).thenReturn(new Fetched(200, html("/blog", "<a href=\"/\">start</a>")));
        when(crawler.fetch("/verwaist")).thenReturn(new Fetched(200, html("/verwaist", "")));
        when(crawler.fetch("/kaputt")).thenReturn(new Fetched(404, ""));
        when(crawler.fetch("/login")).thenReturn(new Fetched(200, html("/login", "")));
        when(crawler.fetch("/duenn")).thenReturn(new Fetched(200, html("/duenn", "").replace("<h1>", "<meta name=\"robots\" content=\"noindex\"><h1>")));
    }

    private Map<String, AuditRow> rows() {
        View view = service.view();
        return view.rows().stream().collect(Collectors.toMap(AuditRow::path, r -> r));
    }

    @Test
    void crawlsSitemapFindsBrokenAndOrphanAndSkipsLanguageVariants() {
        site();

        SeoAuditService.AuditResult result = service.run();

        assertThat(result.status()).isEqualTo(SeoSyncRunEntity.STATUS_SUCCESS);
        assertThat(result.pages()).isEqualTo(4);
        assertThat(result.extraUrls()).isEqualTo(3);
        verify(crawler, never()).fetch("/en/models");

        Map<String, AuditRow> rows = rows();
        assertThat(rows).containsOnlyKeys("/", "/modelle", "/blog", "/verwaist");
        assertThat(rows.get("/modelle").findings()).extracting(Finding::check).containsExactly(Check.BROKEN_LINK, Check.NOINDEX_LINK);
        assertThat(rows.get("/modelle").findings().get(0).message()).contains("/kaputt").contains("404");
        assertThat(rows.get("/modelle").findings().get(1).message()).contains("/duenn");
        assertThat(rows.get("/verwaist").findings()).extracting(Finding::check).containsExactly(Check.ORPHAN);
        assertThat(rows.get("/").findings()).isEmpty();
        assertThat(rows.get("/modelle").errors()).isEqualTo(1);
        assertThat(rows.get("/modelle").warnings()).isEqualTo(1);
        assertThat(rows.get("/modelle").score()).isEqualTo(75);

        View view = service.view();
        assertThat(view.summary().checked()).isEqualTo(4);
        assertThat(view.summary().withErrors()).isEqualTo(1);
        assertThat(view.summary().brokenLinkPages()).isEqualTo(1);
        assertThat(view.summary().orphans()).isEqualTo(1);
    }

    @Test
    void rerunSameDayReplacesRows() {
        site();
        service.run();
        service.run();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM seo_page_audit", Integer.class)).isEqualTo(4);
        assertThat(runs.findAll()).extracting(SeoSyncRunEntity::getSource).containsOnly(SeoAuditService.SOURCE_AUDIT);
    }

    @Test
    void extraUrlsAreCapped() {
        site();
        properties.setMaxExtraUrls(1);

        assertThat(service.run().extraUrls()).isEqualTo(1);
        verify(crawler, never()).fetch("/login");
        verify(crawler, never()).fetch("/duenn");
    }

    @Test
    void unreachablePageMakesRunPartial() {
        site();
        when(crawler.fetch("/blog")).thenReturn(new Fetched(0, null));

        assertThat(service.run().status()).isEqualTo(SeoSyncRunEntity.STATUS_PARTIAL);
        assertThat(rows().get("/blog").findings()).extracting(Finding::check).containsExactly(Check.STATUS);
        // Link von / auf eine nicht erreichbare Sitemap-Seite ist kaputt
        assertThat(rows().get("/").findings()).extracting(Finding::check).containsExactly(Check.BROKEN_LINK);
    }

    @Test
    void sitemapFailureFailsRunWithoutTouchingRows() {
        when(crawler.sitemapPaths()).thenThrow(new SiteCrawlerException("Sitemap HTTP 502"));

        assertThat(service.run().status()).isEqualTo(SeoSyncRunEntity.STATUS_FAILED);
        verify(crawler, never()).fetch(anyString());
        assertThat(runs.findAll()).singleElement().extracting(SeoSyncRunEntity::getError).asString().contains("502");
        assertThat(service.view().day()).isNull();
    }

    @Test
    void viewAddsCoreWebVitalsWarningsAboveThresholds() {
        site();
        service.run();

        SeoAudit.VitalsSnapshot vitals = new SeoAudit.VitalsSnapshot(java.time.LocalDate.of(2026, 10, 19), Map.of(
                "/", new SeoAudit.Vitals(3200, 0.25, 150, "FIELD"),
                "/blog", new SeoAudit.Vitals(1800, 0.02, null, "LAB")));
        Map<String, AuditRow> rows = service.view(vitals).rows().stream().collect(Collectors.toMap(AuditRow::path, r -> r));

        assertThat(rows.get("/").findings()).extracting(Finding::check).containsExactly(Check.CWV, Check.CWV);
        assertThat(rows.get("/").findings()).extracting(Finding::message).anyMatch(m -> m.contains("LCP 3,2 s"));
        assertThat(rows.get("/").warnings()).isEqualTo(2);
        assertThat(rows.get("/").score()).isEqualTo(90);
        assertThat(rows.get("/").vitals().lcpMs()).isEqualTo(3200);
        assertThat(rows.get("/blog").findings()).isEmpty();
        assertThat(service.view(vitals).vitalsWeek()).isEqualTo(java.time.LocalDate.of(2026, 10, 19));
    }
}
