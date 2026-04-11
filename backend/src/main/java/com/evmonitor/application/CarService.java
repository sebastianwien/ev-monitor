package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarService {

    private final CarRepository carRepository;
    private final CoinLogService coinLogService;
    private final CarImageService carImageService;

    @Transactional
    public CarCreateResponse createCar(UUID userId, CarRequest request) {
        Car newCar = Car.createNew(userId, request.model(), request.year(), request.licensePlate(),
                        request.trim(), request.batteryCapacityKwh(), request.powerKw(),
                        request.batteryDegradationPercent())
                .toBuilder()
                .heatPump(request.hasHeatPump())
                .build();

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
        return CarResponse.fromDomain(requireOwnedCar(carId, userId));
    }

    public CarResponse updateCar(UUID carId, UUID userId, CarRequest request) {
        Car existingCar = requireOwnedCar(carId, userId);

        Car updatedCar = existingCar.toBuilder()
                .model(request.model())
                .year(request.year())
                .licensePlate(request.licensePlate())
                .trim(request.trim())
                .batteryCapacityKwh(request.batteryCapacityKwh())
                .powerKw(request.powerKw())
                .batteryDegradationPercent(request.batteryDegradationPercent())
                .heatPump(request.hasHeatPump())
                .updatedAt(LocalDateTime.now())
                .build();

        return CarResponse.fromDomain(carRepository.save(updatedCar));
    }

    public void deleteCar(UUID carId, UUID userId) {
        requireOwnedCar(carId, userId);
        carRepository.deleteById(carId);
        carImageService.deleteImage(carId);
    }

    public Car getCarById(UUID carId) {
        return carRepository.findById(carId)
                .orElseThrow(() -> NotFoundException.forEntity("Car", carId));
    }

    @Transactional
    public CarResponse setActiveCar(UUID carId, UUID userId) {
        Car targetCar = requireOwnedCar(carId, userId);

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
        Car car = requireOwnedCar(carId, userId);
        if (car.getImagePath() == null) {
            throw new ValidationException("Car has no image");
        }
        Car saved = carRepository.save(car.withImage(car.getImagePath(), isPublic));

        // Award public-image bonus (once ever - awardCoinsForEvent enforces idempotency)
        int coinsAwarded = isPublic
                ? coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.IMAGE_PUBLIC, null)
                : 0;
        return new CarImageResponse(CarResponse.fromDomain(saved), coinsAwarded);
    }

    @Transactional
    public CarImageResponse uploadCarImage(UUID userId, UUID carId, MultipartFile file, boolean isPublic) throws IOException {
        Car car = requireOwnedCar(carId, userId);

        String imagePath = carImageService.uploadImage(carId, file);
        Car saved = carRepository.save(car.withImage(imagePath, isPublic));

        // Award first-ever image upload bonus and optional public-image bonus
        // (both are one-time - awardCoinsForEvent enforces idempotency automatically)
        int coinsAwarded = coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.IMAGE_UPLOADED, null);
        if (isPublic) {
            coinsAwarded += coinLogService.awardCoinsForEvent(userId, CoinLogService.CoinEvent.IMAGE_PUBLIC, null);
        }
        return new CarImageResponse(CarResponse.fromDomain(saved), coinsAwarded);
    }

    @Transactional
    public CarResponse setBusinessCar(UUID carId, UUID userId, boolean isBusinessCar) {
        Car car = requireOwnedCar(carId, userId);
        Car saved = carRepository.save(car.withBusinessCar(isBusinessCar));
        return CarResponse.fromDomain(saved);
    }

    @Transactional
    public void deleteCarImage(UUID userId, UUID carId) {
        Car car = requireOwnedCar(carId, userId);
        carImageService.deleteImage(carId);
        carRepository.save(car.withImage(null, false));
    }

    /**
     * Lädt ein Auto und stellt sicher, dass es dem User gehört.
     * Wirft {@link NotFoundException} wenn nicht existent, {@link ForbiddenException}
     * wenn ein anderer User Besitzer ist. Zentralisiert den Ownership-Check aller
     * Service-Methoden.
     */
    private Car requireOwnedCar(UUID carId, UUID userId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> NotFoundException.forEntity("Car", carId));
        if (!car.getUserId().equals(userId)) {
            throw ForbiddenException.notOwner("Car", carId);
        }
        return car;
    }
}
