package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CarResponse(
        UUID id,
        UUID userId,
        CarBrand.CarModel model,
        Integer year,
        String licensePlate,
        String trim,
        BigDecimal batteryCapacityKwh,
        BigDecimal powerKw,
        List<Double> availableCapacities,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CarResponse fromDomain(Car car) {
        return new CarResponse(
                car.getId(),
                car.getUserId(),
                car.getModel(),
                car.getYear(),
                car.getLicensePlate(),
                car.getTrim(),
                car.getBatteryCapacityKwh(),
                car.getPowerKw(),
                car.getModel().getCapacities(),
                car.getCreatedAt(),
                car.getUpdatedAt());
    }
}
