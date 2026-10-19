package com.evmonitor.application;

import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JobLockServiceTest extends AbstractIntegrationTest {

    private static final String NAME = "test_job";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JobLockService service;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM job_lock WHERE name = ?", NAME);
    }

    @Test
    void secondAcquireFailsUntilReleased() {
        assertThat(service.tryAcquire(NAME, Duration.ofHours(1))).isTrue();
        assertThat(service.tryAcquire(NAME, Duration.ofHours(1))).isFalse();

        service.release(NAME);

        assertThat(service.tryAcquire(NAME, Duration.ofHours(1))).isTrue();
    }

    @Test
    void expiredLockCanBeTakenOver() {
        assertThat(service.tryAcquire(NAME, Duration.ofMinutes(5))).isTrue();

        JobLockService later = new JobLockService(jdbc, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(6)));

        assertThat(later.tryAcquire(NAME, Duration.ofMinutes(5))).isTrue();
    }
}
