package com.evmonitor.infrastructure.persistence.seo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Ein Sync-Lauf je Quelle (GSC, später Plausible) für den Tab-Kopf "Letzter Sync". */
@Entity
@Table(name = "seo_sync_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeoSyncRunEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    private UUID id;
    @Column(name = "source", nullable = false, length = 20)
    private String source;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    @Column(name = "status", nullable = false, length = 10)
    private String status;
    @Column(name = "row_count", nullable = false)
    private int rowCount;
    @Column(name = "from_day", nullable = false)
    private LocalDate fromDay;
    @Column(name = "to_day", nullable = false)
    private LocalDate toDay;
    @Column(name = "error", columnDefinition = "TEXT")
    private String error;
}
