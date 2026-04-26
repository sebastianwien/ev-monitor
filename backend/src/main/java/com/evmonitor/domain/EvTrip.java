package com.evmonitor.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ev_trip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvTrip {

    public static final String DATA_SOURCE_TESLA_LIVE    = "TESLA_LIVE";
    public static final String DATA_SOURCE_SMARTCAR_LIVE = "SMARTCAR_LIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "data_source", nullable = false, length = 30)
    private String dataSource;

    @Column(name = "trip_started_at", nullable = false)
    private OffsetDateTime tripStartedAt;

    @Column(name = "trip_ended_at")
    private OffsetDateTime tripEndedAt;

    @Column(name = "soc_start", precision = 5, scale = 2)
    private BigDecimal socStart;

    @Column(name = "soc_end", precision = 5, scale = 2)
    private BigDecimal socEnd;

    @Column(name = "odometer_start_km", precision = 10, scale = 1)
    private BigDecimal odometerStartKm;

    @Column(name = "odometer_end_km", precision = 10, scale = 1)
    private BigDecimal odometerEndKm;

    @Column(name = "distance_km", precision = 8, scale = 1)
    private BigDecimal distanceKm;

    @Column(name = "location_start_geohash", length = 12)
    private String locationStartGeohash;

    @Column(name = "location_end_geohash", length = 12)
    private String locationEndGeohash;

    @Column(name = "outside_temp_celsius", precision = 4, scale = 1)
    private BigDecimal outsideTempCelsius;

    @Column(name = "energy_remaining_start_kwh", precision = 7, scale = 3)
    private BigDecimal energyRemainingStartKwh;

    @Column(name = "energy_remaining_end_kwh", precision = 7, scale = 3)
    private BigDecimal energyRemainingEndKwh;

    @Column(name = "estimated_consumed_kwh", precision = 6, scale = 2)
    private BigDecimal estimatedConsumedKwh;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "route_type", length = 20)
    private String routeType;

    @Column(name = "external_id")
    private UUID externalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "user_edited_at")
    private OffsetDateTime userEditedAt;

    @Column(name = "user_created", nullable = false)
    private boolean userCreated;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = "COMPLETED";
    }
}
