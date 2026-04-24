package com.evmonitor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.evmonitor.domain.CarBrand;

public interface CarRepository {
    Car save(Car car);

    Optional<Car> findById(UUID id);

    List<Car> findAllByUserId(UUID userId);

    List<Car> findAllByModel(CarBrand.CarModel model);

    long countByUserId(UUID userId);

    void deleteById(UUID id);

    /** Cars that have AT_VEHICLE ev_log entries but no SoH entry in the current calendar year. */
    List<Car> findCarsNeedingSohDetection();
}
