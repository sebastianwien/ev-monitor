package com.evmonitor.infrastructure.persistence.xpeng;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "xpeng_import_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpengImportJob {

    public enum Status { QUEUED, PROCESSING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "data_range_start")
    private LocalDateTime dataRangeStart;

    @Column(name = "data_range_end")
    private LocalDateTime dataRangeEnd;

    @Column(name = "imported_trips", nullable = false)
    private int importedTrips;

    @Column(name = "imported_sessions", nullable = false)
    private int importedSessions;

    @Column(name = "skipped_duplicates", nullable = false)
    private int skippedDuplicates;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.QUEUED;
    }
}
