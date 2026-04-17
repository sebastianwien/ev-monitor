package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ev_log")
@Getter
@Setter
@NoArgsConstructor
public class EvLogEntity {

    @Id
    private UUID id;

    @Column(name = "car_id", nullable = false)
    private UUID carId;

    @Column(name = "kwh_charged", nullable = false, precision = 10, scale = 2)
    private BigDecimal kwhCharged;

    @Column(name = "kwh_at_vehicle", precision = 10, scale = 4)
    private BigDecimal kwhAtVehicle;

    @Column(name = "cost_eur", precision = 10, scale = 2)
    private BigDecimal costEur;

    @Column(name = "charge_duration_minutes")
    private Integer chargeDurationMinutes;

    @Column(name = "geohash", length = 7)
    private String geohash;

    @Column(name = "odometer_km")
    private Integer odometerKm;

    @Column(name = "max_charging_power_kw", precision = 10, scale = 2)
    private BigDecimal maxChargingPowerKw;

    @Column(name = "soc_after_charge_percent")
    private Integer socAfterChargePercent;

    @Column(name = "soc_start_percent")
    private Integer socBeforeChargePercent;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    @Column(name = "data_source", length = 50)
    private String dataSource;

    @Column(name = "include_in_statistics", nullable = false)
    private boolean includeInStatistics;

    @Column(name = "odometer_suggestion_min_km")
    private Integer odometerSuggestionMinKm;

    @Column(name = "odometer_suggestion_max_km")
    private Integer odometerSuggestionMaxKm;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "charging_type", length = 10)
    private String chargingType;

    @Column(name = "raw_import_data")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawImportData;

    @Column(name = "route_type", length = 20)
    private String routeType;

    @Column(name = "tire_type", length = 20)
    private String tireType;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "session_group_id")
    private UUID sessionGroupId;

    @Column(name = "is_public_charging", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT false")
    private boolean publicCharging;

    @Column(name = "cpo_name", length = 100)
    private String cpoName;

    @Column(name = "measurement_type", length = 20, nullable = false)
    private String measurementType = "AT_CHARGER";

    @Column(name = "cost_exchange_rate", precision = 10, scale = 6)
    private BigDecimal costExchangeRate;

    @Column(name = "cost_currency", length = 3)
    private String costCurrency;

    @Column(name = "charging_provider_id")
    private UUID chargingProviderId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
