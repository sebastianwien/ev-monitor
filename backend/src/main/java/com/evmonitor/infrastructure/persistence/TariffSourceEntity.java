package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Eine Seite, aus der der Tarif-Crawler die Tarife eines Anbieters liest (V218). */
@Entity
@Table(name = "tariff_source")
@Getter
@Setter
@NoArgsConstructor
public class TariffSourceEntity {

    public enum Mode { EXTRACT, WATCH }

    @Id
    private UUID id;

    @Column(name = "emp_name", nullable = false, length = 100)
    private String empName;

    @Column(name = "url", nullable = false, length = 500, unique = true)
    private String url;

    @Column(name = "mode", nullable = false, length = 10)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Mode mode = Mode.EXTRACT;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_fetched_at")
    private LocalDateTime lastFetchedAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_status", length = 20)
    private String lastStatus;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "approved_hash", length = 64)
    private String approvedHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
