package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plausible-Tagestabellen per JDBC. Ein Datumsfenster wird immer komplett ersetzt; Aggregate kommen
 * gruppiert aus SQL. bounce_rate liegt in Prozent (0-100) in der DB, visit_duration in Sekunden.
 */
@Repository
public class PlausibleDailyRepository {

    public record DayRow(LocalDate day, int visitors, int visits, int pageviews, double bounceRate, int visitDuration) {
    }

    public record PageRow(LocalDate day, String path, int visitors, int pageviews, int entries, double bounceRate,
                          int visitDuration) {
    }

    public record PageGoalRow(LocalDate day, String path, String goal, int visitors) {
    }

    public record SourceRow(LocalDate day, String source, String utmMedium, String utmCampaign, int visitors, int visits) {
    }

    public record SourceGoalRow(LocalDate day, String source, String utmMedium, String utmCampaign, String goal, int visitors) {
    }

    /** Alles, was ein Fenster-Sync schreibt. */
    public record Batch(List<DayRow> days, List<PageRow> pages, List<PageGoalRow> pageGoals, List<SourceRow> sources,
                        List<SourceGoalRow> sourceGoals) {
        public int size() {
            return days.size() + pages.size() + pageGoals.size() + sources.size() + sourceGoals.size();
        }
    }

    /** Aggregat je Pfad; Absprung und Dauer nach Einstiegen gewichtet, bounceRate in Prozent. */
    public record PageTotals(String path, long visitors, long pageviews, long entries, double bounceRate, double visitDuration) {
    }

    public record PageGoalTotals(String path, String goal, long visitors) {
    }

    public record SourceTotals(String source, String utmMedium, String utmCampaign, long visitors, long visits) {
    }

    public record SourceGoalTotals(String source, String utmMedium, String utmCampaign, String goal, long visitors) {
    }

    private static final String ENTRY_WEIGHTED_BOUNCE =
            "CASE WHEN SUM(entries) > 0 THEN SUM(bounce_rate * entries) / SUM(entries) ELSE 0 END";
    private static final String ENTRY_WEIGHTED_DURATION =
            "CASE WHEN SUM(entries) > 0 THEN SUM(visit_duration * entries) / SUM(entries) ELSE 0 END";

    private final JdbcTemplate jdbc;

    public PlausibleDailyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceRange(LocalDate from, LocalDate to, Batch b) {
        Date f = Date.valueOf(from);
        Date t = Date.valueOf(to);
        for (String table : List.of("plausible_day", "plausible_page_daily", "plausible_page_goal_daily",
                "plausible_source_daily", "plausible_source_goal_daily")) {
            jdbc.update("DELETE FROM " + table + " WHERE stat_date BETWEEN ? AND ?", f, t);
        }
        jdbc.batchUpdate("INSERT INTO plausible_day (stat_date, visitors, visits, pageviews, bounce_rate, visit_duration) VALUES (?, ?, ?, ?, ?, ?)",
                b.days(), 500, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setInt(2, r.visitors());
                    ps.setInt(3, r.visits());
                    ps.setInt(4, r.pageviews());
                    ps.setDouble(5, r.bounceRate());
                    ps.setInt(6, r.visitDuration());
                });
        jdbc.batchUpdate("INSERT INTO plausible_page_daily (stat_date, path, visitors, pageviews, entries, bounce_rate, visit_duration) VALUES (?, ?, ?, ?, ?, ?, ?)",
                b.pages(), 500, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setString(2, r.path());
                    ps.setInt(3, r.visitors());
                    ps.setInt(4, r.pageviews());
                    ps.setInt(5, r.entries());
                    ps.setDouble(6, r.bounceRate());
                    ps.setInt(7, r.visitDuration());
                });
        jdbc.batchUpdate("INSERT INTO plausible_page_goal_daily (stat_date, path, goal, visitors) VALUES (?, ?, ?, ?)",
                b.pageGoals(), 500, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setString(2, r.path());
                    ps.setString(3, r.goal());
                    ps.setInt(4, r.visitors());
                });
        jdbc.batchUpdate("INSERT INTO plausible_source_daily (stat_date, source, utm_medium, utm_campaign, visitors, visits) VALUES (?, ?, ?, ?, ?, ?)",
                b.sources(), 500, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setString(2, r.source());
                    ps.setString(3, r.utmMedium());
                    ps.setString(4, r.utmCampaign());
                    ps.setInt(5, r.visitors());
                    ps.setInt(6, r.visits());
                });
        jdbc.batchUpdate("INSERT INTO plausible_source_goal_daily (stat_date, source, utm_medium, utm_campaign, goal, visitors) VALUES (?, ?, ?, ?, ?, ?)",
                b.sourceGoals(), 500, (ps, r) -> {
                    ps.setDate(1, Date.valueOf(r.day()));
                    ps.setString(2, r.source());
                    ps.setString(3, r.utmMedium());
                    ps.setString(4, r.utmCampaign());
                    ps.setString(5, r.goal());
                    ps.setInt(6, r.visitors());
                });
    }

    @Transactional
    public int purgeBefore(LocalDate day) {
        Date d = Date.valueOf(day);
        int removed = 0;
        for (String table : List.of("plausible_day", "plausible_page_daily", "plausible_page_goal_daily",
                "plausible_source_daily", "plausible_source_goal_daily")) {
            removed += jdbc.update("DELETE FROM " + table + " WHERE stat_date < ?", d);
        }
        return removed;
    }

    public boolean hasAnyData() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM plausible_day", Integer.class);
        return n != null && n > 0;
    }

    public Optional<LocalDate> latestDay() {
        Date d = jdbc.queryForObject("SELECT max(stat_date) FROM plausible_day", Date.class);
        return Optional.ofNullable(d).map(Date::toLocalDate);
    }

    public List<DayRow> dayTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT stat_date, visitors, visits, pageviews, bounce_rate, visit_duration FROM plausible_day "
                        + "WHERE stat_date BETWEEN ? AND ? ORDER BY stat_date",
                (rs, i) -> new DayRow(rs.getDate("stat_date").toLocalDate(), rs.getInt("visitors"), rs.getInt("visits"),
                        rs.getInt("pageviews"), rs.getDouble("bounce_rate"), rs.getInt("visit_duration")),
                Date.valueOf(from), Date.valueOf(to));
    }

    /** Google-Einstiege (Visits mit Quelle Google) je Tag, für den Vergleich mit den GSC-Klicks. */
    public Map<LocalDate, Long> googleVisitsByDay(LocalDate from, LocalDate to) {
        Map<LocalDate, Long> result = new HashMap<>();
        jdbc.query("SELECT stat_date, SUM(visits) AS n FROM plausible_source_daily WHERE stat_date BETWEEN ? AND ? "
                        + "AND source = 'Google' GROUP BY stat_date",
                rs -> { result.put(rs.getDate("stat_date").toLocalDate(), rs.getLong("n")); },
                Date.valueOf(from), Date.valueOf(to));
        return result;
    }

    public List<PageTotals> pageTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT path, SUM(visitors) AS visitors, SUM(pageviews) AS pageviews, SUM(entries) AS entries, "
                        + ENTRY_WEIGHTED_BOUNCE + " AS bounce_rate, " + ENTRY_WEIGHTED_DURATION + " AS visit_duration "
                        + "FROM plausible_page_daily WHERE stat_date BETWEEN ? AND ? GROUP BY path",
                (rs, i) -> new PageTotals(rs.getString("path"), rs.getLong("visitors"), rs.getLong("pageviews"),
                        rs.getLong("entries"), rs.getDouble("bounce_rate"), rs.getDouble("visit_duration")),
                Date.valueOf(from), Date.valueOf(to));
    }

    public List<PageGoalTotals> pageGoalTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT path, goal, SUM(visitors) AS visitors FROM plausible_page_goal_daily "
                        + "WHERE stat_date BETWEEN ? AND ? GROUP BY path, goal",
                (rs, i) -> new PageGoalTotals(rs.getString("path"), rs.getString("goal"), rs.getLong("visitors")),
                Date.valueOf(from), Date.valueOf(to));
    }

    public List<SourceTotals> sourceTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT source, utm_medium, utm_campaign, SUM(visitors) AS visitors, SUM(visits) AS visits "
                        + "FROM plausible_source_daily WHERE stat_date BETWEEN ? AND ? GROUP BY source, utm_medium, utm_campaign",
                (rs, i) -> new SourceTotals(rs.getString("source"), rs.getString("utm_medium"), rs.getString("utm_campaign"),
                        rs.getLong("visitors"), rs.getLong("visits")),
                Date.valueOf(from), Date.valueOf(to));
    }

    public List<SourceGoalTotals> sourceGoalTotals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT source, utm_medium, utm_campaign, goal, SUM(visitors) AS visitors "
                        + "FROM plausible_source_goal_daily WHERE stat_date BETWEEN ? AND ? GROUP BY source, utm_medium, utm_campaign, goal",
                (rs, i) -> new SourceGoalTotals(rs.getString("source"), rs.getString("utm_medium"), rs.getString("utm_campaign"),
                        rs.getString("goal"), rs.getLong("visitors")),
                Date.valueOf(from), Date.valueOf(to));
    }
}
