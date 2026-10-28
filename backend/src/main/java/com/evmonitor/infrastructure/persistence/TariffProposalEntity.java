package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Ein Unterschied zwischen Seite und Tabelle aus einem Crawler-Lauf (V218). */
@Entity
@Table(name = "tariff_proposal")
@Getter
@Setter
@NoArgsConstructor
public class TariffProposalEntity {

    public enum Kind { CHANGE, NEW, MISSING, WATCH_CHANGED, MAPPING }

    public enum Status { PENDING, APPLIED, REJECTED, SUPERSEDED }

    @Id
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "emp_name", nullable = false, length = 100)
    private String empName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private Kind kind;

    @Column(name = "tariff_id")
    private UUID tariffId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> proposed;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    @Column(name = "hold_reason", length = 200)
    private String holdReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "seen_count", nullable = false)
    private int seenCount = 1;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by", length = 10)
    private String decidedBy;
}
