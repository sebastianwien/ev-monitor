package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.CoinType;
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
                request.powerKw());

        Car savedCar = carRepository.save(newCar);

        // Award coins: 20 for first car ever, 5 for each subsequent one.
        // Check coin history instead of current car count to prevent delete-and-recreate farming.
        boolean firstCarEver = !coinLogService.hasEverReceivedCoinForAction(
                userId, CoinLogService.ACTION_CAR_CREATED);
        int coins = firstCarEver ? 20 : 5;
        coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, coins, CoinLogService.ACTION_CAR_CREATED);

        return new CarCreateResponse(CarResponse.fromDomain(savedCar), coins);
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
                existingCar.isImagePublic());

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

        // Award public-image bonus (once ever, regardless of upload path)
        int coinsAwarded = 0;
        if (isPublic && !coinLogService.hasEverReceivedCoinForAction(userId, CoinLogService.ACTION_IMAGE_PUBLIC)) {
            coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, 10, CoinLogService.ACTION_IMAGE_PUBLIC);
            coinsAwarded = 10;
        }
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

        // Award first-ever image upload bonus
        int coinsAwarded = 0;
        if (!coinLogService.hasEverReceivedCoinForAction(userId, CoinLogService.ACTION_IMAGE_UPLOADED)) {
            coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, 15, CoinLogService.ACTION_IMAGE_UPLOADED);
            coinsAwarded += 15;
        }
        // Award public-image bonus (once ever, regardless of upload path)
        if (isPublic && !coinLogService.hasEverReceivedCoinForAction(userId, CoinLogService.ACTION_IMAGE_PUBLIC)) {
            coinLogService.awardCoins(userId, CoinType.ACHIEVEMENT_COIN, 10, CoinLogService.ACTION_IMAGE_PUBLIC);
            coinsAwarded += 10;
        }
        return new CarImageResponse(CarResponse.fromDomain(saved), coinsAwarded);
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
