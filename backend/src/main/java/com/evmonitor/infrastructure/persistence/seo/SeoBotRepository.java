package com.evmonitor.infrastructure.persistence.seo;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Crawler-Abrufe je Tag, Bot und Pfad per JDBC. */
@Repository
public class SeoBotRepository {

    public record BotPathHits(String bot, String path, long hits) {
    }

    private final JdbcTemplate jdbc;

    public SeoBotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Zählt einen Abruf. true, wenn dafür eine neue Zeile entstand. Zwei gleichzeitige erste Abrufe: einer zählt per Update. */
    public boolean increment(LocalDate day, String bot, String path) {
        Date d = Date.valueOf(day);
        String update = "UPDATE seo_bot_daily SET hits = hits + 1 WHERE stat_date = ? AND bot = ? AND path = ?";
        if (jdbc.update(update, d, bot, path) > 0) return false;
        try {
            jdbc.update("INSERT INTO seo_bot_daily (stat_date, bot, path, hits) VALUES (?, ?, ?, 1)", d, bot, path);
            return true;
        } catch (DuplicateKeyException e) {
            jdbc.update(update, d, bot, path);
            return false;
        }
    }

    public List<BotPathHits> totals(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT bot, path, sum(hits) FROM seo_bot_daily WHERE stat_date BETWEEN ? AND ? GROUP BY bot, path",
                (rs, i) -> new BotPathHits(rs.getString(1), rs.getString(2), rs.getLong(3)),
                Date.valueOf(from), Date.valueOf(to));
    }

    public int purgeBefore(LocalDate day) {
        return jdbc.update("DELETE FROM seo_bot_daily WHERE stat_date < ?", Date.valueOf(day));
    }
}
