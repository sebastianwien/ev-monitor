package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CarResponse(
        UUID id,
        UUID userId,
        CarBrand brand,
        CarBrand.CarModel model,
        Integer year,
        String licensePlate,
        String trim,
        BigDecimal batteryCapacityKwh,
        BigDecimal powerKw,
        List<Double> availableCapacities,
        LocalDate registrationDate,
        LocalDate deregistrationDate,
        CarStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String imageUrl,
        boolean imagePublic,
        boolean isPrimary,
        BigDecimal batteryDegradationPercent,
        BigDecimal effectiveBatteryCapacityKwh,
        boolean isBusinessCar) {

    public static CarResponse fromDomain(Car car) {
        String imageUrl = car.getImagePath() != null ? "/api/cars/" + car.getId() + "/image" : null;
        return new CarResponse(
                car.getId(),
                car.getUserId(),
                car.getModel().getBrand(),
                car.getModel(),
                car.getYear(),
                car.getLicensePlate(),
                car.getTrim(),
                car.getBatteryCapacityKwh(),
                car.getPowerKw(),
                car.getModel().getCapacities(),
                car.getRegistrationDate(),
                car.getDeregistrationDate(),
                car.getStatus(),
                car.getCreatedAt(),
                car.getUpdatedAt(),
                imageUrl,
                car.isImagePublic(),
                car.isPrimary(),
                car.getBatteryDegradationPercent(),
                car.getEffectiveBatteryCapacityKwh(),
                car.isBusinessCar());
    }
}
