package com.evmonitor.application.seo;

import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Query;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Row;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Events und angelegte Goals der letzten 30 Tage, erfundene Namen. */
class PlausibleEventStatsServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PlausibleEventStatsService service;

    @BeforeEach
    void setUp() {
        reset(plausibleClient);
        service.clearCache();
    }

    /** Der Service ist ein Singleton im geteilten Context: gefüllter Cache würde spätere Tests (AdminSeoController) verfälschen. */
    @AfterEach
    void clearSharedCache() {
        service.clearCache();
    }

    private static Row row(String name, double visitors, double events) {
        return new Row(List.of(name), List.of(visitors, events));
    }

    @Test
    void mergesEventsWithGoals_dropsPageviews_keepsGoalsWithoutEvents() {
        when(plausibleClient.isEnabled()).thenReturn(true);
        when(plausibleClient.query(any(Query.class))).thenAnswer(inv -> {
            Query q = inv.getArgument(0);
            return q.dimensions().equals(List.of("event:name"))
                    ? List.of(row("pageview", 900, 2000), row("demo_clicked", 12, 15), row("old_event", 3, 3))
                    : List.of(row("demo_clicked", 12, 15), row("Startseite aufgerufen", 400, 500));
        });

        PlausibleEventStatsService.EventStats stats = service.last30Days();

        assertThat(stats.enabled()).isTrue();
        assertThat(stats.events()).containsExactlyInAnyOrder(
                new PlausibleEventStatsService.EventStat("demo_clicked", 12, 15, true),
                new PlausibleEventStatsService.EventStat("old_event", 3, 3, false),
                new PlausibleEventStatsService.EventStat("Startseite aufgerufen", 400, 500, true));
    }

    @Test
    void secondCallComesFromCache() {
        when(plausibleClient.isEnabled()).thenReturn(true);
        when(plausibleClient.query(any(Query.class))).thenReturn(List.of());

        service.last30Days();
        service.last30Days();

        // Ein Laden = zwei Abfragen (Events und Goals); ohne Puffer wären es vier.
        verify(plausibleClient, times(2)).query(any(Query.class));
    }

    @Test
    void withoutKey_notCached_soANewKeyWorksAtOnce() {
        when(plausibleClient.isEnabled()).thenReturn(false, true);
        when(plausibleClient.query(any(Query.class))).thenReturn(List.of());

        assertThat(service.last30Days().enabled()).isFalse();
        assertThat(service.last30Days().enabled()).isTrue();
    }

    @Test
    void withoutKey_disabledAndEmpty() {
        when(plausibleClient.isEnabled()).thenReturn(false);

        PlausibleEventStatsService.EventStats stats = service.last30Days();

        assertThat(stats.enabled()).isFalse();
        assertThat(stats.events()).isEmpty();
    }
}
