package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Index-Stand je Sitemap-Pfad per JDBC: Google-Prüfung und IndexNow-Meldung, eine Zeile je Pfad. */
@Repository
public class SeoIndexRepository {

    /** Ergebnis einer URL-Prüfung bei Google; Felder null, wenn Google sie nicht liefert. */
    public record Inspection(String verdict, String coverageState, LocalDateTime lastCrawlAt, String googleCanonical) {
    }

    public record IndexRow(String path, LocalDateTime checkedAt, Inspection inspection, String indexnowHash,
                           LocalDateTime indexnowAt) {
    }

    private final JdbcTemplate jdbc;

    public SeoIndexRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, IndexRow> all() {
        Map<String, IndexRow> out = new LinkedHashMap<>();
        jdbc.query("SELECT path, checked_at, verdict, coverage_state, last_crawl_at, google_canonical, indexnow_hash, indexnow_at FROM seo_page_index",
                rs -> {
                    out.put(rs.getString(1), new IndexRow(rs.getString(1), time(rs.getTimestamp(2)),
                            new Inspection(rs.getString(3), rs.getString(4), time(rs.getTimestamp(5)), rs.getString(6)),
                            rs.getString(7), time(rs.getTimestamp(8))));
                });
        return out;
    }

    @Transactional
    public void saveInspection(String path, LocalDateTime checkedAt, Inspection i) {
        int updated = jdbc.update("UPDATE seo_page_index SET checked_at = ?, verdict = ?, coverage_state = ?, last_crawl_at = ?, google_canonical = ? WHERE path = ?",
                ts(checkedAt), i.verdict(), cut(i.coverageState(), 300), ts(i.lastCrawlAt()), cut(i.googleCanonical(), 1000), path);
        if (updated == 0) {
            jdbc.update("INSERT INTO seo_page_index (path, checked_at, verdict, coverage_state, last_crawl_at, google_canonical) VALUES (?, ?, ?, ?, ?, ?)",
                    path, ts(checkedAt), i.verdict(), cut(i.coverageState(), 300), ts(i.lastCrawlAt()), cut(i.googleCanonical(), 1000));
        }
    }

    /** hashByPath: Inhalts-Hash, der gemeldet wurde, je Pfad. */
    @Transactional
    public void saveIndexNow(Map<String, String> hashByPath, LocalDateTime at) {
        hashByPath.forEach((path, hash) -> {
            int updated = jdbc.update("UPDATE seo_page_index SET indexnow_hash = ?, indexnow_at = ? WHERE path = ?", hash, ts(at), path);
            if (updated == 0) {
                jdbc.update("INSERT INTO seo_page_index (path, indexnow_hash, indexnow_at) VALUES (?, ?, ?)", path, hash, ts(at));
            }
        });
    }

    /** Entfernt Pfade, die nicht mehr in der Sitemap stehen. */
    @Transactional
    public int retainOnly(Collection<String> paths) {
        Set<String> keep = Set.copyOf(paths);
        int removed = 0;
        for (String path : jdbc.queryForList("SELECT path FROM seo_page_index", String.class)) {
            if (!keep.contains(path)) removed += jdbc.update("DELETE FROM seo_page_index WHERE path = ?", path);
        }
        return removed;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static Timestamp ts(LocalDateTime t) {
        return t == null ? null : Timestamp.valueOf(t);
    }

    private static LocalDateTime time(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
