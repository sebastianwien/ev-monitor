package com.evmonitor.application.seo;

import com.evmonitor.infrastructure.external.gsc.GscClient;
import com.evmonitor.infrastructure.external.gsc.GscClient.GscRow;
import com.evmonitor.infrastructure.external.gsc.GscClientException;
import com.evmonitor.infrastructure.persistence.seo.GscDailyRepository;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunEntity;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Sync gegen H2 mit gemocktem GSC-Client: erfundene Pfade und Suchbegriffe, keine echten Exporte. */
class SeoSyncServiceTest extends AbstractIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);
    private static final String HOST = "https://example.test";

    @MockitoBean
    private GscClient gscClient;

    @Autowired
    private SeoSyncService service;
    @Autowired
    private GscDailyRepository repository;
    @Autowired
    private SeoSyncRunRepository runs;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM gsc_page_daily");
        jdbc.update("DELETE FROM gsc_query_daily");
        jdbc.update("DELETE FROM gsc_segment_daily");
        jdbc.update("DELETE FROM seo_sync_run");
        when(gscClient.isEnabled()).thenReturn(true);
    }

    @Test
    void writesNormalizedMergedRowsAndIsIdempotent() {
        stubDay(DAY,
                List.of(row(List.of(HOST + "/modelle/Tesla/Model%203"), 10, 100, 5.0),
                        row(List.of(HOST + "/modelle/Tesla/Model_3/"), 10, 300, 9.0),
                        row(List.of(HOST + "/"), 1, 20, 3.0)),
                List.of(row(List.of("ev monitor", HOST + "/"), 1, 1, 1.0),
                        row(List.of("tesla verbrauch", HOST + "/modelle/Tesla/Model_3"), 5, 50, 6.0)),
                List.of(row(List.of(HOST + "/modelle/Tesla/Model_3", "deu", "MOBILE"), 12, 350, 8.0)));

        SeoSyncService.SyncResult first = service.sync(DAY, DAY);
        SeoSyncService.SyncResult second = service.sync(DAY, DAY);

        assertThat(first.status()).isEqualTo(SeoSyncRunEntity.STATUS_SUCCESS);
        assertThat(first.rows()).isEqualTo(2 + 2 + 1);
        assertThat(second.rows()).isEqualTo(first.rows());

        List<Map<String, Object>> pages = jdbc.queryForList("SELECT * FROM gsc_page_daily ORDER BY path");
        assertThat(pages).hasSize(2);
        Map<String, Object> model3 = pages.get(1);
        assertThat(model3.get("path")).isEqualTo("/modelle/Tesla/Model_3");
        assertThat(((Number) model3.get("clicks")).intValue()).isEqualTo(20);
        assertThat(((Number) model3.get("impressions")).intValue()).isEqualTo(400);
        // impressionsgewichtet: (5*100 + 9*300) / 400 = 8.0
        assertThat(((Number) model3.get("avg_position")).doubleValue()).isEqualTo(8.0);

        List<Map<String, Object>> queries = jdbc.queryForList("SELECT * FROM gsc_query_daily ORDER BY query");
        assertThat(queries).hasSize(2);
        assertThat(queries.get(0).get("query")).isEqualTo("ev monitor");
        assertThat((Boolean) queries.get(0).get("is_brand")).isTrue();
        assertThat((Boolean) queries.get(1).get("is_brand")).isFalse();
        assertThat(queries.get(1).get("path")).isEqualTo("/modelle/Tesla/Model_3");

        List<Map<String, Object>> segments = jdbc.queryForList("SELECT * FROM gsc_segment_daily");
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).get("country")).isEqualTo("deu");
        assertThat(segments.get(0).get("device")).isEqualTo("MOBILE");

        List<SeoSyncRunEntity> runRows = runs.findTop10ByOrderByStartedAtDesc();
        assertThat(runRows).hasSize(2);
        assertThat(runRows.get(0).getSource()).isEqualTo("GSC");
        assertThat(runRows.get(0).getFinishedAt()).isNotNull();
        assertThat(runRows.get(0).getRowCount()).isEqualTo(5);
        assertThat(repository.latestDay()).contains(DAY);
    }

    @Test
    void failingDayDoesNotStopOthersAndRunIsPartial() {
        LocalDate bad = DAY.minusDays(1);
        when(gscClient.query(eq(bad), eq(bad), any())).thenThrow(new GscClientException("GSC query HTTP 500"));
        stubDay(DAY, List.of(row(List.of(HOST + "/modelle"), 2, 30, 4.0)), List.of(), List.of());

        SeoSyncService.SyncResult result = service.sync(bad, DAY);

        assertThat(result.status()).isEqualTo(SeoSyncRunEntity.STATUS_PARTIAL);
        assertThat(result.failedDays()).isEqualTo(1);
        assertThat(result.rows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gsc_page_daily", Integer.class)).isEqualTo(1);
        SeoSyncRunEntity run = runs.findById(result.runId()).orElseThrow();
        assertThat(run.getError()).contains(bad.toString()).contains("500");
    }

    @Test
    void abortsAfterThreeConsecutiveFailuresAndReportsFailed() {
        when(gscClient.query(any(), any(), any())).thenThrow(new GscClientException("GSC token HTTP 401"));

        SeoSyncService.SyncResult result = service.sync(DAY.minusDays(9), DAY);

        assertThat(result.status()).isEqualTo(SeoSyncRunEntity.STATUS_FAILED);
        assertThat(result.days()).isEqualTo(SeoSyncService.MAX_CONSECUTIVE_FAILURES);
        assertThat(result.failedDays()).isEqualTo(SeoSyncService.MAX_CONSECUTIVE_FAILURES);
    }

    @Test
    void purgesRowsOlderThanRetention() {
        LocalDate ancient = LocalDate.now().minusDays(600);
        repository.replaceDay(ancient, List.of(new GscDailyRepository.PageRow(ancient, "/alt", 1, 1, 1.0)), List.of(), List.of());
        stubDay(DAY, List.of(row(List.of(HOST + "/modelle"), 2, 30, 4.0)), List.of(), List.of());

        service.sync(DAY, DAY);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM gsc_page_daily WHERE stat_date = ?", Integer.class, Date.valueOf(ancient)))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gsc_page_daily", Integer.class)).isEqualTo(1);
    }

    private void stubDay(LocalDate day, List<GscRow> pages, List<GscRow> queries, List<GscRow> segments) {
        when(gscClient.query(day, day, List.of("page"))).thenReturn(pages);
        when(gscClient.query(day, day, List.of("query", "page"))).thenReturn(queries);
        when(gscClient.query(day, day, List.of("page", "country", "device"))).thenReturn(segments);
    }

    private static GscRow row(List<String> keys, int clicks, int impressions, double position) {
        return new GscRow(keys, clicks, impressions, position);
    }
}
