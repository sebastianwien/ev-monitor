package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CarDegradationTest {

    private Car carWith(BigDecimal batteryCapacityKwh, BigDecimal degradationPercent) {
        return Car.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).model(CarBrand.CarModel.MODEL_3)
                .year(2024).licensePlate("TEST").trim("Std")
                .batteryCapacityKwh(batteryCapacityKwh).powerKw(new BigDecimal("200"))
                .registrationDate(LocalDate.now()).status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .batteryDegradationPercent(degradationPercent)
                .build();
    }

    @Test
    void getEffectiveBatteryCapacityKwh_nullBattery_returnsNull() {
        Car car = carWith(null, null);
        assertNull(car.getEffectiveBatteryCapacityKwh());
    }

    @Test
    void getEffectiveBatteryCapacityKwh_noDegradation_returnsNominal() {
        Car car = carWith(new BigDecimal("75.0"), null);
        assertEquals(new BigDecimal("75.0"), car.getEffectiveBatteryCapacityKwh());
    }

    @Test
    void getEffectiveBatteryCapacityKwh_zeroDegradation_returnsNominal() {
        Car car = carWith(new BigDecimal("75.0"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("75.0"), car.getEffectiveBatteryCapacityKwh());
    }

    @Test
    void getEffectiveBatteryCapacityKwh_tenPercent_returnsReduced() {
        Car car = carWith(new BigDecimal("75.0"), new BigDecimal("10.0"));
        assertEquals(new BigDecimal("67.50"), car.getEffectiveBatteryCapacityKwh());
    }

    @Test
    void getEffectiveBatteryCapacityKwh_degradationPreservedAcrossWithMethods() {
        Car car = carWith(new BigDecimal("75.0"), new BigDecimal("10.0"));
        Car activated = car.activate();
        assertEquals(new BigDecimal("67.50"), activated.getEffectiveBatteryCapacityKwh());
    }
}
