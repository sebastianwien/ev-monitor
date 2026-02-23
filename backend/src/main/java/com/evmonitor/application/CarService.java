package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CarService {

    private final CarRepository carRepository;

    public CarService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    public CarResponse createCar(UUID userId, CarRequest request) {
        Car newCar = Car.createNew(
                userId,
                request.model(),
                request.year(),
                request.licensePlate(),
                request.trim(),
                request.batteryCapacityKwh(),
                request.powerKw());

        Car savedCar = carRepository.save(newCar);
        return CarResponse.fromDomain(savedCar);
    }

    public List<CarResponse> getCarsForUser(UUID userId) {
        return carRepository.findAllByUserId(userId)
                .stream()
                .map(CarResponse::fromDomain)
                .toList();
    }

    public CarResponse getCarByIdForUser(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        return CarResponse.fromDomain(car);
    }

    public CarResponse updateCar(UUID carId, UUID userId, CarRequest request) {
        Car existingCar = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));

        if (!existingCar.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        Car updatedCar = new Car(
                existingCar.getId(),
                existingCar.getUserId(),
                request.model(),
                request.year(),
                request.licensePlate(),
                request.trim(),
                request.batteryCapacityKwh(),
                request.powerKw(),
                existingCar.getCreatedAt(),
                java.time.LocalDateTime.now());

        Car savedCar = carRepository.save(updatedCar);
        return CarResponse.fromDomain(savedCar);
    }

    public void deleteCar(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        carRepository.deleteById(carId);
    }
}
