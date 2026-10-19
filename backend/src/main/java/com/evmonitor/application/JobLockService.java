package com.evmonitor.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Lock über eine Zeile in {@code job_lock}: {@code UPDATE ... WHERE locked_until < now()} trifft genau
 * eine Instanz. Autocommit je Statement, deshalb kein {@code @Transactional}: ein Duplikat beim Anlegen
 * der Zeile darf keine Transaktion vergiften. Läuft der Halter in den Timeout, fällt der Lock von selbst.
 */
@Service
@Slf4j
public class JobLockService {

    private static final LocalDateTime UNLOCKED = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public JobLockService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    JobLockService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** true, wenn diese Instanz den Lock jetzt hält. */
    public boolean tryAcquire(String name, Duration ttl) {
        LocalDateTime now = LocalDateTime.now(clock);
        ensureRow(name);
        int updated = jdbc.update("UPDATE job_lock SET locked_until = ? WHERE name = ? AND locked_until < ?",
                Timestamp.valueOf(now.plus(ttl)), name, Timestamp.valueOf(now));
        return updated == 1;
    }

    public void release(String name) {
        jdbc.update("UPDATE job_lock SET locked_until = ? WHERE name = ?", Timestamp.valueOf(UNLOCKED), name);
    }

    private void ensureRow(String name) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM job_lock WHERE name = ?", Integer.class, name);
        if (count != null && count > 0) return;
        try {
            jdbc.update("INSERT INTO job_lock (name, locked_until) VALUES (?, ?)", name, Timestamp.valueOf(UNLOCKED));
        } catch (DuplicateKeyException e) {
            log.debug("job_lock {} wurde parallel angelegt", name);
        }
    }
}
