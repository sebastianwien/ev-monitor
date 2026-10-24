package com.evmonitor.application;

import com.evmonitor.infrastructure.persistence.CarOdometerQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Aktueller Kilometerstand für Dashboard-Kachel und Fahrzeugseite, nur für das eigene Auto. */
@Service
@RequiredArgsConstructor
public class CarOdometerService {

    private final CarService carService;
    private final CarOdometerQueryRepository odometerQuery;

    public CarOdometerResponse getCurrentOdometer(UUID carId, UUID userId) {
        carService.requireOwnedCar(carId, userId);
        return new CarOdometerResponse(odometerQuery.findCurrentOdometerKm(carId));
    }
}
