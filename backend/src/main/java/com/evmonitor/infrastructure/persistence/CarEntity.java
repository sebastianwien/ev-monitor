package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "car")
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
    private boolean isPrimary;

    public CarEntity() {
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public CarBrand.CarModel getModel() {
        return model;
    }

    public void setModel(CarBrand.CarModel model) {
        this.model = model;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getTrim() {
        return trim;
    }

    public void setTrim(String trim) {
        this.trim = trim;
    }

    public BigDecimal getBatteryCapacityKwh() {
        return batteryCapacityKwh;
    }

    public void setBatteryCapacityKwh(BigDecimal batteryCapacityKwh) {
        this.batteryCapacityKwh = batteryCapacityKwh;
    }

    public BigDecimal getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(BigDecimal powerKw) {
        this.powerKw = powerKw;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public LocalDate getDeregistrationDate() {
        return deregistrationDate;
    }

    public void setDeregistrationDate(LocalDate deregistrationDate) {
        this.deregistrationDate = deregistrationDate;
    }

    public CarStatus getStatus() {
        return status;
    }

    public void setStatus(CarStatus status) {
        this.status = status;
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

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public boolean isImagePublic() {
        return imagePublic;
    }

    public void setImagePublic(boolean imagePublic) {
        this.imagePublic = imagePublic;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
    }
}
