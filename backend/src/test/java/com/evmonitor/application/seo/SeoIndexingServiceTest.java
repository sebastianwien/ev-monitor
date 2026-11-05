package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoAudit.AuditRow;
import com.evmonitor.application.seo.SeoAudit.Check;
import com.evmonitor.application.seo.SeoAudit.Severity;
import com.evmonitor.application.seo.SeoAudit.View;
import com.evmonitor.application.seo.SeoAudit.VitalsSnapshot;
import com.evmonitor.infrastructure.external.gsc.GscClient.IndexStatus;
import com.evmonitor.infrastructure.external.gsc.GscClientException;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Index-Status und IndexNow nach dem Crawl gegen H2, Google und IndexNow gemockt, erfundene Domain. */
class SeoIndexingServiceTest extends AbstractIntegrationTest {

    private static final String NOINDEX = "[{\"check\":\"NOINDEX\",\"severity\":\"ERROR\",\"message\":\"noindex\"}]";

    @Autowired
    private SeoIndexingService service;
    @Autowired
    private SeoAuditService auditService;
    @Autowired
    private SeoAuditRepository audits;
    @Autowired
    private SeoAuditProperties properties;
    @Autowired
    private JdbcTemplate jdbc;

    private String originalHost;
    private long originalDelay;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM seo_page_audit");
        jdbc.update("DELETE FROM seo_page_index");
        originalHost = properties.getSiteHost();
        originalDelay = properties.getDelayMs();
        properties.setSiteHost("example.test");
        properties.setDelayMs(0);
        when(gscClient.isEnabled()).thenReturn(true);
        when(indexNowClient.isEnabled()).thenReturn(true);
        when(indexNowClient.submit(anyList())).thenReturn(true);
        when(gscClient.inspect("https://example.test/")).thenReturn(
                new IndexStatus("PASS", "Gesendet und indexiert", Instant.parse("2026-11-01T10:00:00Z"), "https://example.test/"));
        when(gscClient.inspect("https://example.test/modelle")).thenReturn(
                new IndexStatus("NEUTRAL", "Gecrawlt - zurzeit nicht indexiert", null, null));
        when(gscClient.inspect("https://example.test/noindex")).thenReturn(
                new IndexStatus("NEUTRAL", "Durch noindex ausgeschlossen", null, null));
        crawled("h-start", "h-modelle");
    }

    @AfterEach
    void restore() {
        properties.setSiteHost(originalHost);
        properties.setDelayMs(originalDelay);
    }

    private void crawled(String startHash, String modelleHash) {
        audits.replaceDay(LocalDate.of(2026, 11, 5), List.of(
                new SeoAuditRepository.AuditRow("/", 200, 100, 0, 0, "[]", startHash),
                new SeoAuditRepository.AuditRow("/modelle", 200, 100, 0, 0, "[]", modelleHash),
                new SeoAuditRepository.AuditRow("/noindex", 200, 80, 1, 0, NOINDEX, "h-noindex"),
                new SeoAuditRepository.AuditRow("/kaputt", 404, 80, 1, 0, "[]", null)));
    }

    @Test
    void inspectsReachablePagesAndSubmitsOnlyChangedIndexablePages() {
        service.run();

        verify(gscClient, times(3)).inspect(anyString());
        verify(gscClient, never()).inspect("https://example.test/kaputt");
        verify(indexNowClient).submit(List.of("/", "/modelle"));

        clearInvocations(indexNowClient);
        service.run();
        verify(indexNowClient, never()).submit(anyList());

        crawled("h-start", "h-modelle-neu");
        service.run();
        verify(indexNowClient).submit(List.of("/modelle"));
    }

    @Test
    void failedSubmitIsRetriedNextRun() {
        when(indexNowClient.submit(anyList())).thenReturn(false);
        service.run();
        when(indexNowClient.submit(anyList())).thenReturn(true);
        clearInvocations(indexNowClient);

        service.run();

        verify(indexNowClient).submit(List.of("/", "/modelle"));
    }

    @Test
    void quotaErrorStopsInspectionButNotIndexNow() {
        when(gscClient.inspect(anyString())).thenThrow(new GscClientException("GSC inspect HTTP 429: quota"));

        service.run();

        verify(gscClient, times(1)).inspect(anyString());
        verify(indexNowClient).submit(List.of("/", "/modelle"));
    }

    @Test
    void manualSubmitOnlyForSitemapPaths() {
        assertThatThrownBy(() -> service.submitManual("/irgendwas")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submitManual("/noindex")).isInstanceOf(IllegalArgumentException.class);

        assertThat(service.submitManual("/modelle")).isTrue();
        verify(indexNowClient).submit(List.of("/modelle"));
        assertThat(service.latest().get("/modelle").indexnowAt()).isNotNull();
    }

    @Test
    void auditViewCarriesIndexStatusAsFinding() {
        when(gscClient.inspect("https://example.test/")).thenReturn(
                new IndexStatus("PASS", "Gesendet und indexiert", null, "https://example.test/?ref=x"));
        service.run();

        View view = auditService.view(VitalsSnapshot.EMPTY, service.latest());

        AuditRow modelle = row(view, "/modelle");
        assertThat(modelle.findings()).anyMatch(f -> f.check() == Check.INDEX && f.severity() == Severity.ERROR
                && f.message().contains("Gecrawlt - zurzeit nicht indexiert"));
        assertThat(modelle.errors()).isEqualTo(1);
        assertThat(modelle.index().verdict()).isEqualTo("NEUTRAL");
        assertThat(modelle.index().indexnowAt()).isNotNull();

        AuditRow start = row(view, "/");
        assertThat(start.findings()).anyMatch(f -> f.check() == Check.INDEX && f.severity() == Severity.WARNING);
        assertThat(start.errors()).isZero();
        assertThat(view.summary().notIndexed()).isEqualTo(2);

        assertThat(row(view, "/kaputt").index()).isNull();
    }

    @Test
    void doesNothingWithoutKeys() {
        when(gscClient.isEnabled()).thenReturn(false);
        when(indexNowClient.isEnabled()).thenReturn(false);

        service.run();

        verify(gscClient, never()).inspect(any());
        verify(indexNowClient, never()).submit(anyList());
    }

    private static AuditRow row(View view, String path) {
        return view.rows().stream().filter(r -> r.path().equals(path)).findFirst().orElseThrow();
    }
}
