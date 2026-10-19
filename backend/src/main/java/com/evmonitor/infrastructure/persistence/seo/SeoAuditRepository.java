package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Prüfberichte je Sitemap-URL und Core Web Vitals per JDBC. Ein Prüftag bzw. eine Woche wird komplett ersetzt. */
@Repository
public class SeoAuditRepository {

    /** findings: JSON-Liste [{check, severity, message}]. */
    public record AuditRow(String path, int httpStatus, int score, int errorCount, int warningCount, String findings) {
    }

    /** source FIELD oder LAB; fehlende Werte null. */
    public record VitalsRow(String path, Integer lcpMs, Double cls, Integer inpMs, String source) {
    }

    private final JdbcTemplate jdbc;

    public SeoAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceDay(LocalDate day, List<AuditRow> rows) {
        Date d = Date.valueOf(day);
        jdbc.update("DELETE FROM seo_page_audit WHERE stat_date = ?", d);
        jdbc.batchUpdate("INSERT INTO seo_page_audit (stat_date, path, http_status, score, error_count, warning_count, findings) VALUES (?, ?, ?, ?, ?, ?, ?)",
                rows, 500, (ps, r) -> {
                    ps.setDate(1, d);
                    ps.setString(2, r.path());
                    ps.setInt(3, r.httpStatus());
                    ps.setInt(4, r.score());
                    ps.setInt(5, r.errorCount());
                    ps.setInt(6, r.warningCount());
                    ps.setString(7, r.findings());
                });
    }

    @Transactional
    public void replaceWeek(LocalDate weekStart, List<VitalsRow> rows) {
        Date w = Date.valueOf(weekStart);
        jdbc.update("DELETE FROM seo_page_vitals WHERE week_start = ?", w);
        jdbc.batchUpdate("INSERT INTO seo_page_vitals (week_start, path, lcp_ms, cls, inp_ms, source) VALUES (?, ?, ?, ?, ?, ?)",
                rows, 500, (ps, r) -> {
                    ps.setDate(1, w);
                    ps.setString(2, r.path());
                    if (r.lcpMs() == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, r.lcpMs());
                    if (r.cls() == null) ps.setNull(4, Types.NUMERIC); else ps.setBigDecimal(4, BigDecimal.valueOf(r.cls()));
                    if (r.inpMs() == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, r.inpMs());
                    ps.setString(6, r.source());
                });
    }

    @Transactional
    public int purgeBefore(LocalDate day) {
        Date d = Date.valueOf(day);
        return jdbc.update("DELETE FROM seo_page_audit WHERE stat_date < ?", d)
                + jdbc.update("DELETE FROM seo_page_vitals WHERE week_start < ?", d);
    }

    public Optional<LocalDate> latestDay() {
        Date d = jdbc.queryForObject("SELECT max(stat_date) FROM seo_page_audit", Date.class);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }

    public List<AuditRow> rows(LocalDate day) {
        return jdbc.query("SELECT path, http_status, score, error_count, warning_count, findings FROM seo_page_audit WHERE stat_date = ? ORDER BY score, path",
                (rs, i) -> new AuditRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getString(6)),
                Date.valueOf(day));
    }

    public Optional<LocalDate> latestVitalsWeek() {
        Date d = jdbc.queryForObject("SELECT max(week_start) FROM seo_page_vitals", Date.class);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }

    public List<VitalsRow> vitals(LocalDate weekStart) {
        return jdbc.query("SELECT path, lcp_ms, cls, inp_ms, source FROM seo_page_vitals WHERE week_start = ?",
                (rs, i) -> new VitalsRow(rs.getString(1), (Integer) rs.getObject(2),
                        rs.getBigDecimal(3) == null ? null : rs.getBigDecimal(3).doubleValue(), (Integer) rs.getObject(4), rs.getString(5)),
                Date.valueOf(weekStart));
    }
}
