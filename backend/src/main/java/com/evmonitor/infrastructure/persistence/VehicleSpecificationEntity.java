package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "vehicle_specification",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"car_brand", "car_model", "battery_capacity_kwh", "wltp_type"}
    )
)
public class VehicleSpecificationEntity {

    @Id
    private UUID id;

    @Column(name = "car_brand", nullable = false)
    private String carBrand;

    @Column(name = "car_model", nullable = false)
    private String carModel;

    @Column(name = "battery_capacity_kwh", nullable = false, precision = 10, scale = 2)
    private BigDecimal batteryCapacityKwh;

    @Column(name = "wltp_range_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal wltpRangeKm;

    @Column(name = "wltp_consumption_kwh_per_100km", nullable = false, precision = 10, scale = 2)
    private BigDecimal wltpConsumptionKwhPer100km;

    @Column(name = "wltp_type", nullable = false)
    private String wltpType;

    @Column(name = "variant_name")
    private String variantName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public VehicleSpecificationEntity() {
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCarBrand() {
        return carBrand;
    }

    public void setCarBrand(String carBrand) {
        this.carBrand = carBrand;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public BigDecimal getBatteryCapacityKwh() {
        return batteryCapacityKwh;
    }

    public void setBatteryCapacityKwh(BigDecimal batteryCapacityKwh) {
        this.batteryCapacityKwh = batteryCapacityKwh;
    }

    public BigDecimal getWltpRangeKm() {
        return wltpRangeKm;
    }

    public void setWltpRangeKm(BigDecimal wltpRangeKm) {
        this.wltpRangeKm = wltpRangeKm;
    }

    public BigDecimal getWltpConsumptionKwhPer100km() {
        return wltpConsumptionKwhPer100km;
    }

    public void setWltpConsumptionKwhPer100km(BigDecimal wltpConsumptionKwhPer100km) {
        this.wltpConsumptionKwhPer100km = wltpConsumptionKwhPer100km;
    }

    public String getWltpType() {
        return wltpType;
    }

    public void setWltpType(String wltpType) {
        this.wltpType = wltpType;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
