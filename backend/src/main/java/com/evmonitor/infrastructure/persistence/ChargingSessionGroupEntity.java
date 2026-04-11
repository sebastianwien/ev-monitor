package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "charging_session_group")
@Getter
@Setter
@NoArgsConstructor
public class ChargingSessionGroupEntity {

    @Id
    private UUID id;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "total_kwh_charged", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalKwhCharged;

    @Column(name = "total_duration_minutes")
    private Integer totalDurationMinutes;

    @Column(name = "session_start", nullable = false)
    private LocalDateTime sessionStart;

    @Column(name = "session_end", nullable = false)
    private LocalDateTime sessionEnd;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "geohash", length = 5)
    private String geohash;

    @Column(name = "cost_eur", precision = 10, scale = 2)
    private BigDecimal costEur;

    @Column(name = "data_source", length = 50, nullable = false)
    private String dataSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
