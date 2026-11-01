package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charging_site_question",
        uniqueConstraints = @UniqueConstraint(name = "uq_charging_site_question", columnNames = {"user_id", "geohash", "charging_type"}))
@Getter
@Setter
@NoArgsConstructor
public class ChargingSiteQuestionEntity {

    /** Ladeart unbekannt - leer statt NULL, damit der Unique-Schlüssel greift. */
    static final String UNKNOWN_TYPE = "";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "geohash", nullable = false, length = 7)
    private String geohash;

    @Column(name = "charging_type", nullable = false, length = 10)
    private String chargingType;

    @Column(name = "candidates", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String candidates;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
