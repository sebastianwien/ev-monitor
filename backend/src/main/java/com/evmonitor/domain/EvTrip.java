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

    public static final String DATA_SOURCE_TESLA_LIVE     = "TESLA_LIVE";
    public static final String DATA_SOURCE_SMARTCAR_LIVE  = "SMARTCAR_LIVE";
    public static final String DATA_SOURCE_TESLA_INFERRED = "TESLA_INFERRED";
    public static final String DATA_SOURCE_API_UPLOAD     = "API_UPLOAD";

    /**
     * Live-detected data sources that require AutoSync-Live entitlement to view or edit.
     * All other sources (TESSIE, USER_CREATED, etc.) are user-owned data and stay
     * accessible to the owner regardless of subscription tier.
     */
    public static final java.util.Set<String> LIVE_TRIP_SOURCES = java.util.Set.of(
            DATA_SOURCE_TESLA_LIVE, DATA_SOURCE_SMARTCAR_LIVE, DATA_SOURCE_TESLA_INFERRED);

    public boolean isLiveSource() {
        return dataSource != null && LIVE_TRIP_SOURCES.contains(dataSource);
    }

    /**
     * Obergrenze fuer aggregierte und pro-Sample Trip-Geschwindigkeiten. Muss
     * mit dem V118 DB-CHECK (avg_speed_kmh/max_speed_kmh BETWEEN 0 AND 300)
     * uebereinstimmen - die DB ist die Wahrheit, hier nur der spiegelnde Wert
     * fuer Write-Time-Validation.
     */
    public static final BigDecimal SPEED_KMH_MAX = new BigDecimal("300");

    /**
     * Kappt einen Geschwindigkeitswert auf den V118-Range [0, 300] km/h. Werte
     * ausserhalb (Sensor-Sentinels, negative Glitches) werden zu null - lieber
     * kein Wert als verseuchte Aggregation oder Insert-Fail. Zentrale Stelle
     * fuer Speed-Quality-Checks, von Tessie (Trip-Aggregat) und XPeng
     * (per-row Sample) gleichermassen genutzt.
     */
    public static BigDecimal clampSpeedKmh(BigDecimal v) {
        if (v == null) return null;
        if (v.signum() < 0) return null;
        if (v.compareTo(SPEED_KMH_MAX) > 0) return null;
        return v;
    }

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

    /** Herkunft von {@link #outsideTempCelsius}; null = unbekannt, siehe Temperatur-Backfill. */
    @Enumerated(EnumType.STRING)
    @Column(name = "outside_temp_source", length = 16)
    private com.evmonitor.domain.weather.TemperatureSource outsideTempSource;

    @Column(name = "energy_remaining_start_kwh", precision = 7, scale = 3)
    private BigDecimal energyRemainingStartKwh;

    @Column(name = "energy_remaining_end_kwh", precision = 7, scale = 3)
    private BigDecimal energyRemainingEndKwh;

    @Column(name = "estimated_consumed_kwh", precision = 6, scale = 2)
    private BigDecimal estimatedConsumedKwh;

    @Column(name = "avg_speed_kmh", precision = 5, scale = 2)
    private BigDecimal avgSpeedKmh;

    @Column(name = "max_speed_kmh", precision = 5, scale = 2)
    private BigDecimal maxSpeedKmh;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "route_type", length = 20)
    private String routeType;

    /**
     * Vom Router gerechnete Linie als encodierte Polyline. Was sie zeigt, sagt
     * {@link #routeKind}: entweder den Weg zwischen Start- und Zielgegend, der von der Fahrt
     * selbst nichts weiss, oder die auf Strassen gelegte {@link #tracePolyline}.
     */
    @Column(name = "route_polyline")
    private String routePolyline;

    /**
     * Woher {@link #routePolyline} stammt: {@code SKETCH} zwischen Start- und Zielgegend
     * geraten, {@code MATCHED} entlang der Stuetzpunkte der gefahrenen Spur gerechnet.
     * Ohne die Angabe waere die Spalte nicht deutbar.
     */
    @Column(name = "route_kind", length = 10)
    private String routeKind;

    /**
     * Die gefahrene Linie als encodierte Polyline, aus den Location-Beacons der Telemetrie
     * (Tesla-FULL mit Location-Scope). Stuetzpunkte im Minutenabstand, jeder das Zentrum
     * seiner Geohash-8-Zelle - eine Fahrtspur, keine Streckenmessung.
     */
    @Column(name = "trace_polyline")
    private String tracePolyline;

    @Column(name = "external_id")
    private UUID externalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    /**
     * Source-spezifische aggregierte Telematik-Daten (Driving-Style, Batterie-Temp, Motor-Werte etc.)
     * als JSON. Quelle und Schema-Version stecken im JSON unter {@code source} und {@code schema_version}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "telemetry_extras", columnDefinition = "jsonb")
    private String telemetryExtras;

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
