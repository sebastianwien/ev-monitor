package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Wettbewerbsdaten (DataForSEO) per JDBC. Eine Woche bzw. ein Monat wird beim Abruf komplett ersetzt. */
@Repository
public class SeoCompetitionRepository {

    public record SerpRow(String query, int rank, String domain, String url, String title) {
    }

    /** cpc in US-Dollar wie von Google Ads geliefert; fehlende Werte null. */
    public record KeywordMetric(String query, Integer searchVolume, Double cpc, String competition) {
    }

    public record KeywordIdea(String keyword, String seed, Integer searchVolume, Double cpc) {
    }

    private final JdbcTemplate jdbc;

    public SeoCompetitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceSerpWeek(LocalDate week, List<SerpRow> rows) {
        Date w = Date.valueOf(week);
        jdbc.update("DELETE FROM serp_snapshot WHERE week_start = ?", w);
        jdbc.batchUpdate("INSERT INTO serp_snapshot (week_start, query, serp_rank, domain, url, title) VALUES (?, ?, ?, ?, ?, ?)",
                rows, 500, (ps, r) -> {
                    ps.setDate(1, w);
                    ps.setString(2, r.query());
                    ps.setInt(3, r.rank());
                    ps.setString(4, r.domain());
                    ps.setString(5, r.url());
                    ps.setString(6, r.title());
                });
    }

    @Transactional
    public void replaceMetricsMonth(LocalDate month, List<KeywordMetric> rows) {
        Date m = Date.valueOf(month);
        jdbc.update("DELETE FROM keyword_metrics WHERE month_start = ?", m);
        jdbc.batchUpdate("INSERT INTO keyword_metrics (month_start, query, search_volume, cpc, competition) VALUES (?, ?, ?, ?, ?)",
                rows, 500, (ps, r) -> {
                    ps.setDate(1, m);
                    ps.setString(2, r.query());
                    setInt(ps, 3, r.searchVolume());
                    setMoney(ps, 4, r.cpc());
                    ps.setString(5, r.competition());
                });
    }

    @Transactional
    public void replaceIdeasMonth(LocalDate month, List<KeywordIdea> rows) {
        Date m = Date.valueOf(month);
        jdbc.update("DELETE FROM keyword_idea WHERE month_start = ?", m);
        jdbc.batchUpdate("INSERT INTO keyword_idea (month_start, keyword, seed, search_volume, cpc) VALUES (?, ?, ?, ?, ?)",
                rows, 500, (ps, r) -> {
                    ps.setDate(1, m);
                    ps.setString(2, r.keyword());
                    ps.setString(3, r.seed());
                    setInt(ps, 4, r.searchVolume());
                    setMoney(ps, 5, r.cpc());
                });
    }

    public Optional<LocalDate> latestSerpWeek() {
        return latest("SELECT max(week_start) FROM serp_snapshot");
    }

    public Optional<LocalDate> latestMetricsMonth() {
        return latest("SELECT max(month_start) FROM keyword_metrics");
    }

    public Optional<LocalDate> latestIdeasMonth() {
        return latest("SELECT max(month_start) FROM keyword_idea");
    }

    public List<SerpRow> serpRows(LocalDate week) {
        return jdbc.query("SELECT query, serp_rank, domain, url, title FROM serp_snapshot WHERE week_start = ? ORDER BY query, serp_rank",
                (rs, i) -> new SerpRow(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                Date.valueOf(week));
    }

    public List<KeywordMetric> metrics(LocalDate month) {
        return jdbc.query("SELECT query, search_volume, cpc, competition FROM keyword_metrics WHERE month_start = ?",
                (rs, i) -> new KeywordMetric(rs.getString(1), intOrNull(rs, 2), moneyOrNull(rs, 3), rs.getString(4)),
                Date.valueOf(month));
    }

    public List<KeywordIdea> ideas(LocalDate month) {
        return jdbc.query("SELECT keyword, seed, search_volume, cpc FROM keyword_idea WHERE month_start = ?",
                (rs, i) -> new KeywordIdea(rs.getString(1), rs.getString(2), intOrNull(rs, 3), moneyOrNull(rs, 4)),
                Date.valueOf(month));
    }

    /** Summe der Kosten einer Quelle seit einem Zeitpunkt (Monatsbudget). */
    public double costSince(String source, LocalDateTime since) {
        BigDecimal sum = jdbc.queryForObject("SELECT sum(cost_usd) FROM seo_sync_run WHERE source = ? AND started_at >= ?",
                BigDecimal.class, source, Timestamp.valueOf(since));
        return sum == null ? 0 : sum.doubleValue();
    }

    @Transactional
    public int purgeBefore(LocalDate day) {
        Date d = Date.valueOf(day);
        return jdbc.update("DELETE FROM serp_snapshot WHERE week_start < ?", d)
                + jdbc.update("DELETE FROM keyword_metrics WHERE month_start < ?", d)
                + jdbc.update("DELETE FROM keyword_idea WHERE month_start < ?", d);
    }

    private Optional<LocalDate> latest(String sql) {
        Date d = jdbc.queryForObject(sql, Date.class);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }

    private static void setInt(java.sql.PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }

    private static void setMoney(java.sql.PreparedStatement ps, int i, Double v) throws SQLException {
        if (v == null) ps.setNull(i, Types.NUMERIC); else ps.setBigDecimal(i, BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private static Integer intOrNull(ResultSet rs, int i) throws SQLException {
        int v = rs.getInt(i);
        return rs.wasNull() ? null : v;
    }

    private static Double moneyOrNull(ResultSet rs, int i) throws SQLException {
        BigDecimal v = rs.getBigDecimal(i);
        return v == null ? null : v.doubleValue();
    }
}
