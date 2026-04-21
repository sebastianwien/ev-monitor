package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.exception.ForbiddenException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for CarService.
 * Tests car CRUD operations and ownership validation.
 *
 * SECURITY CRITICAL: Users must only access/modify their own cars!
 */
@ExtendWith(MockitoExtension.class)
class CarServiceTest {

    @Mock
    private CarRepository carRepository;

    @Mock
    private CoinLogService coinLogService;

    @Mock
    private CarImageService carImageService;

    private CarService carService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        carService = new CarService(carRepository, coinLogService, carImageService);
        userId = UUID.randomUUID();
        carId = UUID.randomUUID();
    }

    @Test
    void shouldCreateCar_WithOwnershipLink() {
        // Given
        CarRequest request = new CarRequest(
                CarBrand.CarModel.MODEL_3,
                2024,
                "TEST-123",
                "Long Range",
                new BigDecimal("79.0"),
                new BigDecimal("350.0"),
                null,
                false,
                null
        );

        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Simulate: user has never received a car-creation coin before → first-time bonus
        when(coinLogService.hasEverReceivedCoinForAction(userId, CoinLogService.CoinEvent.CAR_CREATED_FIRST.getDescription()))
                .thenReturn(false);
        when(coinLogService.awardCoinsForEvent(eq(userId), eq(CoinLogService.CoinEvent.CAR_CREATED_FIRST), isNull()))
                .thenReturn(CoinLogService.CoinEvent.CAR_CREATED_FIRST.getDefaultAmount());

        // When
        CarCreateResponse response = carService.createCar(userId, request);

        // Then
        assertNotNull(response);
        assertEquals(CarBrand.CarModel.MODEL_3, response.car().model());
        assertEquals(2024, response.car().year());
        assertEquals(20, response.coinsAwarded(), "First car must award 20 coins");

        // Verify ownership link
        ArgumentCaptor<Car> carCaptor = ArgumentCaptor.forClass(Car.class);
        verify(carRepository).save(carCaptor.capture());
        Car savedCar = carCaptor.getValue();
        assertEquals(userId, savedCar.getUserId(), "Car must be linked to user");
    }

    @Test
    void shouldGetCarsForUser_OnlyOwnCars() {
        // Given: User has 2 cars
        Car car1 = TestDataBuilder.createTestCarWithId(UUID.randomUUID(), userId, CarBrand.CarModel.MODEL_3);
        Car car2 = TestDataBuilder.createTestCarWithId(UUID.randomUUID(), userId, CarBrand.CarModel.I4);

        when(carRepository.findAllByUserId(userId)).thenReturn(List.of(car1, car2));

        // When
        List<CarResponse> cars = carService.getCarsForUser(userId);

        // Then
        assertEquals(2, cars.size());
        verify(carRepository).findAllByUserId(userId);
    }

    @Test
    void shouldGetCarById_OnlyIfOwner() {
        // Given: User owns this car
        Car ownedCar = TestDataBuilder.createTestCarWithId(carId, userId, CarBrand.CarModel.MODEL_3);
        when(carRepository.findById(carId)).thenReturn(Optional.of(ownedCar));

        // When
        CarResponse response = carService.getCarByIdForUser(carId, userId);

        // Then
        assertNotNull(response);
        assertEquals(carId, response.id());
    }

    @Test
    void shouldRejectGetCarById_IfNotOwner() {
        // Given: Car belongs to another user
        UUID otherUserId = UUID.randomUUID();
        Car otherUserCar = TestDataBuilder.createTestCarWithId(carId, otherUserId, CarBrand.CarModel.I4);

        when(carRepository.findById(carId)).thenReturn(Optional.of(otherUserCar));

        // When & Then
        assertThrows(ForbiddenException.class, () -> carService.getCarByIdForUser(carId, userId));
    }

    @Test
    void shouldRejectGetCarById_IfNotFound() {
        // Given: Car doesn't exist
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> carService.getCarByIdForUser(carId, userId));
    }

    @Test
    void shouldUpdateCar_OnlyIfOwner() {
        // Given: User owns this car
        Car existingCar = TestDataBuilder.createTestCarWithId(carId, userId, CarBrand.CarModel.MODEL_3);
        when(carRepository.findById(carId)).thenReturn(Optional.of(existingCar));
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarRequest updateRequest = new CarRequest(
                CarBrand.CarModel.MODEL_3,
                2025,
                "NEW-456",
                "Performance",
                new BigDecimal("82.0"),
                new BigDecimal("450.0"),
                null,
                false,
                null
        );

        // When
        CarResponse response = carService.updateCar(carId, userId, updateRequest);

        // Then
        assertNotNull(response);
        assertEquals(2025, response.year());
        assertEquals("NEW-456", response.licensePlate());
        assertEquals("Performance", response.trim());

        // Verify ownership preserved
        ArgumentCaptor<Car> carCaptor = ArgumentCaptor.forClass(Car.class);
        verify(carRepository).save(carCaptor.capture());
        Car updatedCar = carCaptor.getValue();
        assertEquals(userId, updatedCar.getUserId(), "Ownership must not change");
    }

    @Test
    void shouldRejectUpdate_IfNotOwner() {
        // Given: Car belongs to another user
        UUID otherUserId = UUID.randomUUID();
        Car otherUserCar = TestDataBuilder.createTestCarWithId(carId, otherUserId, CarBrand.CarModel.I4);

        when(carRepository.findById(carId)).thenReturn(Optional.of(otherUserCar));

        CarRequest updateRequest = new CarRequest(
                CarBrand.CarModel.I4,
                2025,
                "HACKED-123",
                "Stolen",
                new BigDecimal("80.0"),
                new BigDecimal("400.0"),
                null,
                false,
                null
        );

        // When & Then
        assertThrows(ForbiddenException.class, () -> carService.updateCar(carId, userId, updateRequest));

        // Verify no save happened
        verify(carRepository, never()).save(any(Car.class));
    }

    @Test
    void shouldDeleteCar_OnlyIfOwner() {
        // Given: User owns this car
        Car ownedCar = TestDataBuilder.createTestCarWithId(carId, userId, CarBrand.CarModel.MODEL_3);
        when(carRepository.findById(carId)).thenReturn(Optional.of(ownedCar));

        // When
        carService.deleteCar(carId, userId);

        // Then
        verify(carRepository).deleteById(carId);
    }

    @Test
    void shouldRejectDelete_IfNotOwner() {
        // Given: Car belongs to another user
        UUID otherUserId = UUID.randomUUID();
        Car otherUserCar = TestDataBuilder.createTestCarWithId(carId, otherUserId, CarBrand.CarModel.I4);

        when(carRepository.findById(carId)).thenReturn(Optional.of(otherUserCar));

        // When & Then
        assertThrows(ForbiddenException.class, () -> carService.deleteCar(carId, userId));

        // Verify no deletion happened
        verify(carRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void shouldRejectDelete_IfNotFound() {
        // Given: Car doesn't exist
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> carService.deleteCar(carId, userId));

        // Verify no deletion happened
        verify(carRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void shouldSetActiveCar_DeactivatesAllOthers() {
        // Given: User has two cars; car1 is currently primary, car2 is not
        UUID carId1 = UUID.randomUUID();
        UUID carId2 = UUID.randomUUID();
        Car car1 = TestDataBuilder.createTestCarWithId(carId1, userId, CarBrand.CarModel.MODEL_3).activate();
        Car car2 = TestDataBuilder.createTestCarWithId(carId2, userId, CarBrand.CarModel.I4);

        when(carRepository.findById(carId2)).thenReturn(Optional.of(car2));
        when(carRepository.findAllByUserId(userId)).thenReturn(List.of(car1, car2));
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: Set car2 as active
        CarResponse response = carService.setActiveCar(carId2, userId);

        // Then: car2 is now primary
        assertTrue(response.isPrimary(), "Target car must be primary after activation");

        // Verify car1 was deactivated and car2 was activated
        ArgumentCaptor<Car> captor = ArgumentCaptor.forClass(Car.class);
        verify(carRepository, times(2)).save(captor.capture());
        List<Car> savedCars = captor.getAllValues();

        Car savedDeactivated = savedCars.stream().filter(c -> c.getId().equals(carId1)).findFirst().orElseThrow();
        Car savedActivated   = savedCars.stream().filter(c -> c.getId().equals(carId2)).findFirst().orElseThrow();

        assertFalse(savedDeactivated.isPrimary(), "Previously primary car must be deactivated");
        assertTrue(savedActivated.isPrimary(),    "Target car must be activated");
    }

    @Test
    void shouldRejectSetActiveCar_IfNotOwner() {
        // Given: Car belongs to a different user
        UUID otherUserId = UUID.randomUUID();
        Car otherUserCar = TestDataBuilder.createTestCarWithId(carId, otherUserId, CarBrand.CarModel.I4);

        when(carRepository.findById(carId)).thenReturn(Optional.of(otherUserCar));

        // When & Then
        assertThrows(ForbiddenException.class, () -> carService.setActiveCar(carId, userId));
        verify(carRepository, never()).save(any(Car.class));
    }

    @Test
    void shouldPersistBatteryDegradationPercent_WhenUpdatingCar() {
        // Given
        Car existingCar = TestDataBuilder.createTestCarWithId(carId, userId, CarBrand.CarModel.MODEL_3);
        when(carRepository.findById(carId)).thenReturn(Optional.of(existingCar));
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarRequest updateRequest = new CarRequest(
                CarBrand.CarModel.MODEL_3,
                2024,
                "TEST-456",
                "Performance",
                new BigDecimal("75.0"),
                new BigDecimal("200.0"),
                new BigDecimal("10.0"),
                false,
                null
        );

        // When
        carService.updateCar(carId, userId, updateRequest);

        // Then
        ArgumentCaptor<Car> captor = ArgumentCaptor.forClass(Car.class);
        verify(carRepository).save(captor.capture());
        Car savedCar = captor.getValue();
        assertEquals(new BigDecimal("10.0"), savedCar.getBatteryDegradationPercent());
        // 75 kWh - 10% = 67.5 kWh
        assertEquals(new BigDecimal("67.50"), savedCar.getEffectiveBatteryCapacityKwh());
    }

    @Test
    void shouldRejectSetActiveCar_IfNotFound() {
        // Given: Car doesn't exist
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () -> carService.setActiveCar(carId, userId));
        verify(carRepository, never()).save(any(Car.class));
    }
}
