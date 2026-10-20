package com.evmonitor.application.seo;

import com.evmonitor.application.seo.SeoAudit.Vitals;
import com.evmonitor.application.seo.SeoAudit.VitalsSnapshot;
import com.evmonitor.infrastructure.external.audit.PageSpeedClient;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository.PageTotals;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository.AuditRow;
import com.evmonitor.infrastructure.persistence.seo.SeoAuditRepository.VitalsRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoVitalsServiceTest {

    /** Mittwoch 21.10.2026, Woche ab Montag 19.10. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);
    private static final LocalDate AUDIT_DAY = LocalDate.of(2026, 10, 21);

    private final PageSpeedClient client = mock(PageSpeedClient.class);
    private final GscDailyRepository gsc = mock(GscDailyRepository.class);
    private final SeoAuditRepository repository = mock(SeoAuditRepository.class);
    private final SeoAuditProperties properties = new SeoAuditProperties();
    private SeoVitalsService service;

    @BeforeEach
    void setUp() {
        properties.setBaseUrl("https://example.test");
        properties.setSiteHost("example.test");
        properties.setPagespeedApiKey("key-1");
        properties.setVitalsTopPages(2);
        service = new SeoVitalsService(client, gsc, repository, properties, CLOCK);
        when(client.isEnabled()).thenReturn(true);
        when(repository.latestDay()).thenReturn(Optional.of(AUDIT_DAY));
        when(repository.rows(AUDIT_DAY)).thenReturn(List.of(
                new AuditRow("/", 200, 100, 0, 0, "[]"),
                new AuditRow("/modelle", 200, 100, 0, 0, "[]"),
                new AuditRow("/modelle/A/B", 200, 100, 0, 0, "[]"),
                new AuditRow("/kaputt", 404, 80, 1, 0, "[]")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void measuresTopPagesByImpressionsOncePerWeek() {
        when(repository.latestVitalsWeek()).thenReturn(Optional.of(WEEK.minusWeeks(1)));
        when(gsc.pageTotals(AUDIT_DAY.minusDays(28), AUDIT_DAY.minusDays(1))).thenReturn(List.of(
                new PageTotals("/", 10, 100, 3),
                new PageTotals("/kaputt", 50, 9000, 2),
                new PageTotals("/modelle/A/B", 40, 5000, 4),
                new PageTotals("/nicht-in-sitemap", 99, 99999, 1)));
        when(client.measure("https://example.test/modelle/A/B")).thenReturn(Optional.of(new Vitals(2000, 0.01, 100, "FIELD")));
        when(client.measure("https://example.test/")).thenReturn(Optional.empty());

        assertThat(service.refreshIfDue()).isEqualTo(1);

        ArgumentCaptor<List<VitalsRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(repository).replaceWeek(eq(WEEK), rows.capture());
        assertThat(rows.getValue()).containsExactly(new VitalsRow("/modelle/A/B", 2000, 0.01, 100, "FIELD"));
        verify(client, never()).measure("https://example.test/kaputt");
    }

    @Test
    void skipsWhenThisWeekIsMeasuredOrDisabled() {
        when(repository.latestVitalsWeek()).thenReturn(Optional.of(WEEK));
        assertThat(service.refreshIfDue()).isZero();

        properties.setPagespeedApiKey("");
        when(repository.latestVitalsWeek()).thenReturn(Optional.empty());
        assertThat(service.refreshIfDue()).isZero();
        verify(client, never()).measure(anyString());
        verify(repository, never()).replaceWeek(any(), any());
    }

    @Test
    void latestSnapshotMapsRowsByPath() {
        when(repository.latestVitalsWeek()).thenReturn(Optional.of(WEEK));
        when(repository.vitals(WEEK)).thenReturn(List.of(new VitalsRow("/a", 3000, null, null, "LAB")));

        VitalsSnapshot s = service.latest();
        assertThat(s.week()).isEqualTo(WEEK);
        assertThat(s.byPath()).containsEntry("/a", new Vitals(3000, null, null, "LAB"));

        when(repository.latestVitalsWeek()).thenReturn(Optional.empty());
        assertThat(service.latest()).isEqualTo(VitalsSnapshot.EMPTY);
    }
}
