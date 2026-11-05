package com.evmonitor.infrastructure.persistence.seo;

import com.evmonitor.infrastructure.persistence.seo.SeoIndexRepository.Inspection;
import com.evmonitor.testutil.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** V226 gegen Postgres mit Flyway: Hash-Spalte, Index-Stand und Bot-Zähler schreiben und lesen. Übersprungen ohne Docker. */
class SeoIndexingPostgresIT extends AbstractPostgresIT {

    @Autowired SeoAuditRepository audits;
    @Autowired SeoIndexRepository index;
    @Autowired SeoBotRepository bots;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM seo_page_audit");
        jdbc.update("DELETE FROM seo_page_index");
        jdbc.update("DELETE FROM seo_bot_daily");
    }

    @Test
    void writesHashIndexStateAndBotHits() {
        LocalDate day = LocalDate.of(2026, 11, 5);
        audits.replaceDay(day, List.of(new SeoAuditRepository.AuditRow("/modelle", 200, 100, 0, 0, "[]", "a".repeat(64))));
        assertThat(audits.rows(day).get(0).contentHash()).isEqualTo("a".repeat(64));

        LocalDateTime at = LocalDateTime.of(2026, 11, 5, 5, 10);
        index.saveIndexNow(Map.of("/modelle", "a".repeat(64)), at);
        index.saveInspection("/modelle", at, new Inspection("PASS", "x".repeat(400), at.minusDays(2), "https://example.test/modelle"));
        index.saveInspection("/weg", at, new Inspection("NEUTRAL", null, null, null));
        assertThat(index.retainOnly(List.of("/modelle"))).isEqualTo(1);

        SeoIndexRepository.IndexRow row = index.all().get("/modelle");
        assertThat(row.indexnowAt()).isEqualTo(at);
        assertThat(row.inspection().verdict()).isEqualTo("PASS");
        assertThat(row.inspection().coverageState()).hasSize(300);

        assertThat(bots.increment(day, "GPTBOT", "/modelle")).isTrue();
        assertThat(bots.increment(day, "GPTBOT", "/modelle")).isFalse();
        assertThat(bots.totals(day, day)).containsExactly(new SeoBotRepository.BotPathHits("GPTBOT", "/modelle", 2));
        assertThat(bots.purgeBefore(day.plusDays(1))).isEqualTo(1);
    }
}
