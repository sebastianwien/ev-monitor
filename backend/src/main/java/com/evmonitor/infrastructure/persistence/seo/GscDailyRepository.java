package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Schreib- und Lesezugriff auf die GSC-Tagestabellen per JDBC. Ein Tag wird immer komplett ersetzt,
 * Aggregate kommen gruppiert aus SQL, der Rest (Median, Filter) wird in Java gerechnet, damit H2 und
 * Postgres dasselbe liefern.
 */
@Repository
public class GscDailyRepository {

    public record PageRow(LocalDate day, String path, int clicks, int impressions, double avgPosition) {
    }

    public record QueryRow(LocalDate day, String query, String path, int clicks, int impressions,
                           double avgPosition, boolean brand) {
    }

    public record SegmentRow(LocalDate day, String path, String country, String device, int clicks, int impressions) {
    }

    /** Aggregat je Pfad über ein Fenster; Position impressionsgewichtet. */
    /** Impressionen und Klicks je Tag, getrennt nach Brand (is_brand) und Non-Brand. */
    public record BrandDay(LocalDate day, boolean brand, long impressions, long clicks) {
    }

    public record PageTotals(String path, long clicks, long impressions, double avgPosition) {
    }

    public record DayTotals(LocalDate day, long clicks, long impressions, double avgPosition) {
    }

    /** Aggregat je Suchbegriff über ein Fenster, plus beste Seite (meiste Klicks) und Seitenanzahl mit Impressionen. */
    public record QueryTotals(String query, boolean brand, long clicks, long impressions, double avgPosition,
                              String topPath, int pageCount) {
    }

    private static final String WEIGHTED_POSITION =
            "CASE WHEN SUM(impressions) > 0 THEN SUM(avg_position * impressions) / SUM(impressions) ELSE 0 END";

    private final JdbcTemplate jdbc;

    public GscDailyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceDay(LocalDate day, List<PageRow> pages, List<QueryRow> queries, List<SegmentRow> segments) {
        Date d = Date.valueOf(day);
        jdbc.update("DELETE FROM gsc_page_daily WHERE stat_date = ?", d);
        jdbc.update("DELETE FROM gsc_query_daily WHERE stat_date = ?", d);
        jdbc.update("DELETE FROM gsc_segment_daily WHERE stat_date = ?", d);
        jdbc.batchUpdate("INSERT INTO gsc_page_daily (stat_date, path, clicks, impressions, avg_position) VALUES (?, ?, ?, ?, ?)",
                pages, 500, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setString(2, r.path());
                    ps.setInt(3, r.clicks());
                    ps.setInt(4, r.impressions());
                    ps.setDouble(5, r.avgPosition());
                });
        jdbc.batchUpdate("INSERT INTO gsc_query_daily (stat_date, query, path, clicks, impressions, avg_position, is_brand) VALUES (?, ?, ?, ?, ?, ?, ?)",
                queries, 500, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setString(2, r.query());
                    ps.setString(3, r.path());
                    ps.setInt(4, r.clicks());
                    ps.setInt(5, r.impressions());
                    ps.setDouble(6, r.avgPosition());
                    ps.setBoolean(7, r.brand());
                });
        jdbc.batchUpdate("INSERT INTO gsc_segment_daily (stat_date, path, country, device, clicks, impressions) VALUES (?, ?, ?, ?, ?, ?)",
                segments, 500, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setString(2, r.path());
                    ps.setString(3, r.country());
                    ps.setString(4, r.device());
                    ps.setInt(5, r.clicks());
                    ps.setInt(6, r.impressions());
                });
    }

    @Transactional
    public int purgeBefore(LocalDate day) {
        Date d = Date.valueOf(day);
        int removed = jdbc.update("DELETE FROM gsc_page_daily WHERE stat_date < ?", d);
        removed += jdbc.update("DELETE FROM gsc_query_daily WHERE stat_date < ?", d);
        removed += jdbc.update("DELETE FROM gsc_segment_daily WHERE stat_date < ?", d);
        return removed;
    }

    public boolean hasAnyData() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM gsc_page_daily", Integer.class);
        return n != null && n > 0;
    }

    public Optional<LocalDate> latestDay() {
        Date d = jdbc.queryForObject("SELECT max(stat_date) FROM gsc_page_daily", Date.class);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }

    public List<PageTotals> pageTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT path, SUM(clicks) AS clicks, SUM(impressions) AS impressions, "
                        + WEIGHTED_POSITION + " AS avg_position FROM gsc_page_daily WHERE stat_date BETWEEN ? AND ? GROUP BY path",
                (rs, i) -> new PageTotals(rs.getString("path"), rs.getLong("clicks"), rs.getLong("impressions"),
                        rs.getDouble("avg_position")),
                Date.valueOf(from), Date.valueOf(to));
    }

    public List<DayTotals> dayTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT stat_date, SUM(clicks) AS clicks, SUM(impressions) AS impressions, "
                        + WEIGHTED_POSITION + " AS avg_position FROM gsc_page_daily WHERE stat_date BETWEEN ? AND ? GROUP BY stat_date ORDER BY stat_date",
                (rs, i) -> new DayTotals(rs.getDate("stat_date").toLocalDate(), rs.getLong("clicks"), rs.getLong("impressions"),
                        rs.getDouble("avg_position")),
                Date.valueOf(from), Date.valueOf(to));
    }

    /** Erster Tag mit GSC-Daten; ohne Daten leer. */
    public Optional<LocalDate> earliestDay() {
        Date d = jdbc.queryForObject("SELECT min(stat_date) FROM gsc_page_daily", Date.class);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }

    /** Rohzeilen je (Tag, Pfad), damit der Service nach Markt filtern kann. */
    public List<PageRow> pageDays(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT stat_date, path, clicks, impressions, avg_position FROM gsc_page_daily WHERE stat_date BETWEEN ? AND ?",
                (rs, i) -> new PageRow(rs.getDate("stat_date").toLocalDate(), rs.getString("path"), rs.getInt("clicks"),
                        rs.getInt("impressions"), rs.getDouble("avg_position")),
                Date.valueOf(from), Date.valueOf(to));
    }

    /** Suchbegriff-Ebene je Tag und Brand/Non-Brand; anonymisierte Begriffe liefert GSC nicht, sie fehlen hier. */
    public List<BrandDay> brandDayTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT stat_date, is_brand, SUM(impressions) AS impressions, SUM(clicks) AS clicks FROM gsc_query_daily "
                        + "WHERE stat_date BETWEEN ? AND ? GROUP BY stat_date, is_brand",
                (rs, i) -> new BrandDay(rs.getDate("stat_date").toLocalDate(), rs.getBoolean("is_brand"),
                        rs.getLong("impressions"), rs.getLong("clicks")),
                Date.valueOf(from), Date.valueOf(to));
    }

    /** Pfade, die im Fenster mindestens eine Impression hatten. */
    public List<String> pathsWithImpressions(LocalDate from, LocalDate to) {
        return jdbc.queryForList("SELECT path FROM gsc_page_daily WHERE stat_date BETWEEN ? AND ? GROUP BY path HAVING SUM(impressions) > 0",
                String.class, Date.valueOf(from), Date.valueOf(to));
    }

    /** Aggregat je (Suchbegriff, Pfad); die Verdichtung auf den Suchbegriff passiert im Service. */
    public List<QueryRow> queryPathTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT query, path, is_brand, SUM(clicks) AS clicks, SUM(impressions) AS impressions, "
                        + WEIGHTED_POSITION + " AS avg_position FROM gsc_query_daily WHERE stat_date BETWEEN ? AND ? "
                        + "GROUP BY query, path, is_brand",
                (rs, i) -> new QueryRow(null, rs.getString("query"), rs.getString("path"), rs.getInt("clicks"),
                        rs.getInt("impressions"), rs.getDouble("avg_position"), rs.getBoolean("is_brand")),
                Date.valueOf(from), Date.valueOf(to));
    }
}
