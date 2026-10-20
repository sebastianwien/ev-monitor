package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mini-Lock für Jobs, die im Blue/Green-Deploy auf beiden Instanzen starten könnten.
 * Verwaltet über {@link com.evmonitor.application.JobLockService}; Entity nur für das Schema.
 */
@Entity
@Table(name = "job_lock")
@Getter
@NoArgsConstructor
public class JobLockEntity {

    @Id
    @Column(name = "name", nullable = false, length = 50)
    private String name;
    @Column(name = "locked_until", nullable = false)
    private LocalDateTime lockedUntil;
}
