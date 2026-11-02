package com.evmonitor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface JpaFuelPriceMonthRepository extends JpaRepository<FuelPriceMonthEntity, LocalDate> {
}
