package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class CarService {

    private final CarRepository carRepository;
    private final CoinLogService coinLogService;
    private final CarImageService carImageService;

    public CarService(CarRepository carRepository, CoinLogService coinLogService, CarImageService carImageService) {
        this.carRepository = carRepository;
        this.coinLogService = coinLogService;
        this.carImageService = carImageService;
    }

    @Transactional
    public CarCreateResponse createCar(UUID userId, CarRequest request) {
        Car newCar = Car.createNew(
                userId,
                request.model(),
                request.year(),
                request.licensePlate(),
                request.trim(),
                request.batteryCapacityKwh(),
                request.powerKw(),
                request.batteryDegradationPercent());

        Car savedCar = carRepository.save(newCar);

        // Award coins: 20 for first car ever, 5 for each subsequent one.
        // Check coin history instead of current car count to prevent delete-and-recreate farming.
        boolean firstCarEver = !coinLogService.hasEverReceivedCoinForAction(
                userId, CoinLogService.CoinEvent.CAR_CREATED_FIRST.getDescription());
        CoinLogService.CoinEvent carEvent = firstCarEver
                ? CoinLogService.CoinEvent.CAR_CREATED_FIRST
                : CoinLogService.CoinEvent.CAR_CREATED_SUBSEQUENT;
        int coinsAwarded = coinLogService.awardCoinsForEvent(userId, carEvent, null);

        return new CarCreateResponse(CarResponse.fromDomain(savedCar), coinsAwarded);
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
                existingCar.getRegistrationDate(),
                existingCar.getDeregistrationDate(),
                existingCar.getStatus(),
                existingCar.getCreatedAt(),
                java.time.LocalDateTime.now(),
                existingCar.getImagePath(),
                existingCar.isImagePublic(),
                existingCar.isPrimary(),
                request.batteryDegradationPercent(),
                existingCar.isBusinessCar());

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
        carImageService.deleteImage(carId);
    }

    public Car getCarById(UUID carId) {
        return carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));
    }

    @Transactional
    public CarResponse setActiveCar(UUID carId, UUID userId) {
        Car targetCar = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));

        if (!targetCar.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        // Deactivate all cars for this user, then activate the target
        List<Car> allCars = carRepository.findAllByUserId(userId);
        for (Car car : allCars) {
            if (car.isPrimary()) {
                carRepository.save(car.deactivate());
            }
        }

        Car activated = carRepository.save(targetCar.activate());
        return CarResponse.fromDomain(activated);
    }

    @Transactional
    public CarImageResponse updateCarImageVisibility(UUID userId, UUID carId, boolean isPublic) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }
        if (car.getImagePath() == null) {
            throw new IllegalArgumentException("Car has no image");
        }
        Car saved = carRepository.save(car.withImage(car.getImagePath(), isPublic));

        // Award public-image bonus (once ever — awardCoinsForEvent enforces idempotency)
        int coinsAwarded = isPublic
                ? coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.IMAGE_PUBLIC, null)
                : 0;
        return new CarImageResponse(CarResponse.fromDomain(saved), coinsAwarded);
    }

    @Transactional
    public CarImageResponse uploadCarImage(UUID userId, UUID carId, MultipartFile file, boolean isPublic) throws IOException {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        String imagePath = carImageService.uploadImage(carId, file);
        Car saved = carRepository.save(car.withImage(imagePath, isPublic));

        // Award first-ever image upload bonus and optional public-image bonus
        // (both are one-time — awardCoinsForEvent enforces idempotency automatically)
        int coinsAwarded = coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.IMAGE_UPLOADED, null);
        if (isPublic) {
            coinsAwarded += coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.IMAGE_PUBLIC, null);
        }
        return new CarImageResponse(CarResponse.fromDomain(saved), coinsAwarded);
    }

    @Transactional
    public CarResponse setBusinessCar(UUID carId, UUID userId, boolean isBusinessCar) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));
        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }
        Car saved = carRepository.save(car.withBusinessCar(isBusinessCar));
        return CarResponse.fromDomain(saved);
    }

    @Transactional
    public void deleteCarImage(UUID userId, UUID carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found with ID: " + carId));

        if (!car.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own the specified car");
        }

        carImageService.deleteImage(carId);
        Car updated = car.withImage(null, false);
        carRepository.save(updated);
    }
}
