package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "vehicle_specification",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"car_brand", "car_model", "battery_capacity_kwh", "variant_name", "wltp_type", "rating_source"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class VehicleSpecificationEntity {

    @Id
    private UUID id;

    @Column(name = "car_brand", nullable = false)
    private String carBrand;

    @Column(name = "car_model", nullable = false)
    private String carModel;

    @Column(name = "battery_capacity_kwh", nullable = false, precision = 10, scale = 2)
    private BigDecimal batteryCapacityKwh;

    @Column(name = "official_range_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal officialRangeKm;

    @Column(name = "official_consumption_kwh_per_100km", nullable = false, precision = 10, scale = 2)
    private BigDecimal officialConsumptionKwhPer100km;

    @Column(name = "wltp_type", nullable = false)
    private String wltpType;

    @Column(name = "rating_source", nullable = false)
    private String ratingSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "variant_name", nullable = false)
    private String variantName = "";

    @Column(name = "net_battery_capacity_kwh", precision = 10, scale = 2)
    private BigDecimal netBatteryCapacityKwh;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "available_to")
    private LocalDate availableTo;

    @Column(name = "trim_level")
    private String trimLevel;
}
