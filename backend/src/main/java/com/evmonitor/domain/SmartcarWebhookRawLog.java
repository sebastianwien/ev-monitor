package com.evmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "smartcar_webhook_raw_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartcarWebhookRawLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "smartcar_vehicle_id", nullable = false, length = 36)
    private String smartcarVehicleId;

    @Column(name = "make", length = 64)
    private String make;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "vehicle_year")
    private Integer year;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggers", nullable = false)
    private String triggers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signals", nullable = false)
    private String signals;

    @Column(name = "soc_percent", precision = 5, scale = 2)
    private BigDecimal socPercent;

    @Column(name = "odometer_km", precision = 10, scale = 1)
    private BigDecimal odometerKm;

    @Column(name = "location_geohash", length = 8)
    private String locationGeohash;

    @Column(name = "outside_temp_celsius", precision = 5, scale = 2)
    private BigDecimal outsideTempCelsius;

    @Column(name = "mode", length = 16)
    private String mode;

    @PrePersist
    void prePersist() {
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
    }
}
