package com.evmonitor.infrastructure.persistence.seo;

import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.KeywordIdea;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.KeywordMetric;
import com.evmonitor.infrastructure.persistence.seo.SeoCompetitionRepository.SerpRow;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Schreiben, Ersetzen, Lesen und Kostensumme gegen H2; erfundene Begriffe und Domains. */
class SeoCompetitionRepositoryTest extends AbstractIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);
    private static final LocalDate MONTH = LocalDate.of(2026, 10, 1);

    @Autowired
    private SeoCompetitionRepository repository;
    @Autowired
    private SeoSyncRunRepository runs;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        for (String t : List.of("serp_snapshot", "keyword_metrics", "keyword_idea", "seo_sync_run")) jdbc.update("DELETE FROM " + t);
    }

    @Test
    void replacesWeekAndMonthAndReadsBack() {
        repository.replaceSerpWeek(WEEK, List.of(new SerpRow("testauto", 1, "a.test", "https://a.test/", "A")));
        repository.replaceSerpWeek(WEEK, List.of(new SerpRow("testauto", 1, "b.test", "https://b.test/", null),
                new SerpRow("testauto", 2, "c.test", "https://c.test/", "C")));
        repository.replaceMetricsMonth(MONTH, List.of(new KeywordMetric("testauto", 1300, 0.845, "LOW"), new KeywordMetric("leer", null, null, null)));
        repository.replaceIdeasMonth(MONTH, List.of(new KeywordIdea("testauto winter", "testauto", 210, 1.2)));

        assertThat(repository.latestSerpWeek()).contains(WEEK);
        assertThat(repository.serpRows(WEEK)).extracting(SerpRow::domain).containsExactly("b.test", "c.test");
        assertThat(repository.metrics(MONTH)).contains(new KeywordMetric("testauto", 1300, 0.85, "LOW"), new KeywordMetric("leer", null, null, null));
        assertThat(repository.ideas(MONTH)).containsExactly(new KeywordIdea("testauto winter", "testauto", 210, 1.2));

        assertThat(repository.purgeBefore(MONTH.plusMonths(1))).isEqualTo(5);
        assertThat(repository.latestSerpWeek()).isEmpty();
    }

    @Test
    void costSinceSumsOnlyTheSourceSinceTheGivenTime() {
        SeoSyncRunEntity old = SeoSyncRunEntity.start("SERP", LocalDateTime.of(2026, 9, 30, 5, 30), MONTH, MONTH);
        old.setCostUsd(new BigDecimal("2.0"));
        SeoSyncRunEntity now = SeoSyncRunEntity.start("SERP", LocalDateTime.of(2026, 10, 5, 5, 30), MONTH, MONTH);
        now.setCostUsd(new BigDecimal("0.1234"));
        SeoSyncRunEntity other = SeoSyncRunEntity.start("GSC", LocalDateTime.of(2026, 10, 5, 4, 30), MONTH, MONTH);
        runs.saveAll(List.of(old, now, other));

        assertThat(repository.costSince("SERP", MONTH.atStartOfDay())).isEqualTo(0.1234);
        assertThat(repository.costSince("PLAUSIBLE", MONTH.atStartOfDay())).isZero();
    }
}
