package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "car")
@Getter
@Setter
@NoArgsConstructor
public class CarEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CarBrand.CarModel model;

    @Column(name = "manufacture_year", nullable = false)
    private Integer year;

    @Column(name = "license_plate")
    private String licensePlate;

    @Column(name = "trim")
    private String trim;

    @Column(name = "battery_capacity_kwh")
    private BigDecimal batteryCapacityKwh;

    @Column(name = "power_kw")
    private BigDecimal powerKw;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "deregistration_date")
    private LocalDate deregistrationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CarStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "image_public", nullable = false)
    private boolean imagePublic;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "battery_degradation_percent")
    private BigDecimal batteryDegradationPercent;

    @Column(name = "is_business_car", nullable = false)
    private boolean businessCar;

    @Column(name = "has_heat_pump", nullable = false)
    private boolean heatPump;
}
