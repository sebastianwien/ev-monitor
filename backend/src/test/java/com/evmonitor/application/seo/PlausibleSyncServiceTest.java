package com.evmonitor.application.seo;

import com.evmonitor.infrastructure.external.plausible.PlausibleClient;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Query;
import com.evmonitor.infrastructure.external.plausible.PlausibleClient.Row;
import com.evmonitor.infrastructure.external.plausible.PlausibleClientException;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.PlausibleDailyRepository.PageTotals;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunEntity;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Sync gegen H2 mit gemocktem Plausible-Client: erfundene Pfade und Quellen. */
class PlausibleSyncServiceTest extends AbstractIntegrationTest {

    private static final LocalDate D1 = LocalDate.of(2026, 10, 14);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 15);

    @MockitoBean
    private PlausibleClient client;

    @Autowired
    private PlausibleSyncService service;
    @Autowired
    private PlausibleDailyRepository repository;
    @Autowired
    private PlausibleProperties properties;
    @Autowired
    private SeoSyncRunRepository runs;
    @Autowired
    private JdbcTemplate jdbc;

    private List<String> originalGoals;
    private int originalWindow;

    @BeforeEach
    void clean() {
        for (String t : List.of("plausible_day", "plausible_page_daily", "plausible_page_goal_daily",
                "plausible_source_daily", "plausible_source_goal_daily", "seo_sync_run")) {
            jdbc.update("DELETE FROM " + t);
        }
        originalGoals = properties.getGoals();
        originalWindow = properties.getWindowDays();
        properties.setGoals(List.of("gate_code_verified"));
        when(client.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void restore() {
        properties.setGoals(originalGoals);
        properties.setWindowDays(originalWindow);
    }

    @Test
    void writesNormalizedMergedRowsAndIsIdempotent() {
        when(client.query(any())).thenAnswer(inv -> answer(inv.getArgument(0)));

        service.sync(D1, D2);
        service.sync(D1, D2);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM plausible_day", Integer.class)).isEqualTo(2);
        Map<String, PageTotals> pages = repository.pageTotals(D1, D2).stream()
                .collect(java.util.stream.Collectors.toMap(PageTotals::path, p -> p));
        // "/modelle/Tesla/Model%203" und "/modelle/Tesla/Model_3/" fallen zusammen
        PageTotals model3 = pages.get("/modelle/Tesla/Model_3");
        assertThat(model3.visitors()).isEqualTo(30);
        assertThat(model3.pageviews()).isEqualTo(45);
        assertThat(model3.entries()).isEqualTo(10 + 5);
        // Absprung nach Einstiegen gewichtet: (80*10 + 20*5) / 15 = 60
        assertThat(model3.bounceRate()).isEqualTo(60.0);
        assertThat(pages.get("/").entries()).isEqualTo(7);
        assertThat(pages).doesNotContainKey("/modelle/Tesla/Model%203");

        assertThat(repository.pageGoalTotals(D1, D2))
                .anySatisfy(g -> {
                    assertThat(g.path()).isEqualTo("/modelle/Tesla/Model_3");
                    assertThat(g.goal()).isEqualTo("gate_code_verified");
                    assertThat(g.visitors()).isEqualTo(3);
                });
        assertThat(repository.sourceTotals(D1, D2))
                .anySatisfy(s -> {
                    assertThat(s.source()).isEqualTo("Google");
                    assertThat(s.utmMedium()).isEmpty();
                    assertThat(s.utmCampaign()).isEmpty();
                    assertThat(s.visits()).isEqualTo(40);
                })
                .anySatisfy(s -> {
                    assertThat(s.source()).isEqualTo("vw-forum");
                    assertThat(s.utmMedium()).isEqualTo("forum");
                });
        assertThat(repository.googleVisitsByDay(D1, D2)).containsEntry(D1, 40L);
        assertThat(repository.sourceGoalTotals(D1, D2)).singleElement()
                .satisfies(s -> assertThat(s.visitors()).isEqualTo(2));
        assertThat(repository.latestDay()).contains(D2);

        List<SeoSyncRunEntity> log = runs.findTop10ByOrderByStartedAtDesc();
        assertThat(log).hasSize(2);
        assertThat(log.get(0).getSource()).isEqualTo("PLAUSIBLE");
        assertThat(log.get(0).getStatus()).isEqualTo(SeoSyncRunEntity.STATUS_SUCCESS);
        // 2 Tage, 2 Seiten (Model_3 zusammengefallen, "/"), 1 Goal-Zeile, 2 Quellen, 1 Quelle x Goal
        assertThat(log.get(0).getRowCount()).isEqualTo(2 + 2 + 1 + 2 + 1);
    }

    @Test
    void brokenWindowLeavesOthersAndEndsPartial() {
        properties.setWindowDays(1);
        when(client.query(any())).thenAnswer(inv -> {
            Query q = inv.getArgument(0);
            if (q.from().equals(D2)) throw new PlausibleClientException("HTTP 500");
            return answer(q);
        });

        var result = service.sync(D1, D2);

        assertThat(result.status()).isEqualTo(SeoSyncRunEntity.STATUS_PARTIAL);
        assertThat(result.days()).isEqualTo(2);
        assertThat(result.failedDays()).isEqualTo(1);
        assertThat(repository.latestDay()).contains(D1);
        assertThat(runs.findTop10ByOrderByStartedAtDesc().get(0).getError()).contains("2026-10-15").contains("HTTP 500");
    }

    /** Antwort je Abfrageart, erkannt an Dimensionen und Filter. Nur Tag D1 hat Zeilen, D2 nur die Tagessumme. */
    private static List<Row> answer(Query q) {
        List<String> d = q.dimensions();
        if (d.equals(List.of("time:day"))) {
            List<Row> both = List.of(row(List.of(D1.toString()), 100, 120, 400, 35, 210), row(List.of(D2.toString()), 90, 100, 300, 30, 180));
            return both.stream().filter(r -> {
                LocalDate day = LocalDate.parse(r.dim(0));
                return !day.isBefore(q.from()) && !day.isAfter(q.to());
            }).toList();
        }
        if (d.equals(List.of("time:day", "event:page")) && q.goals() == null) {
            return List.of(row(List.of(D1.toString(), "/modelle/Tesla/Model%203"), 20, 30),
                    row(List.of(D1.toString(), "/modelle/Tesla/Model_3/"), 10, 15),
                    row(List.of(D1.toString(), "/"), 50, 60));
        }
        if (d.equals(List.of("time:day", "visit:entry_page")) && q.goals() == null) {
            return List.of(row(List.of(D1.toString(), "/modelle/Tesla/Model%203"), 9, 10, 80, 100),
                    row(List.of(D1.toString(), "/modelle/Tesla/Model_3"), 5, 5, 20, 400),
                    row(List.of(D1.toString(), "/"), 7, 7, 50, 30));
        }
        if (d.equals(List.of("time:day", "visit:entry_page"))) {
            return List.of(row(List.of(D1.toString(), "/modelle/Tesla/Model%203"), 2),
                    row(List.of(D1.toString(), "/modelle/Tesla/Model_3/"), 1));
        }
        if (d.size() == 4) {
            return List.of(row(List.of(D1.toString(), "Google", "(not set)", "(not set)"), 35, 40),
                    row(List.of(D1.toString(), "vw-forum", "forum", "vorstellung"), 4, 4));
        }
        if (d.size() == 5) {
            return List.of(row(List.of(D1.toString(), "vw-forum", "forum", "vorstellung", "gate_code_verified"), 2));
        }
        throw new IllegalArgumentException("unerwartete Abfrage " + d);
    }

    private static Row row(List<String> dims, double... metrics) {
        return new Row(dims, java.util.Arrays.stream(metrics).boxed().toList());
    }
}
