package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
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
import static org.mockito.ArgumentMatchers.any;
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

    private CarService carService;

    private UUID userId;
    private UUID carId;

    @BeforeEach
    void setUp() {
        carService = new CarService(carRepository);
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
                new BigDecimal("350.0")
        );

        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CarResponse response = carService.createCar(userId, request);

        // Then
        assertNotNull(response);
        assertEquals(CarBrand.CarModel.MODEL_3, response.model());
        assertEquals(2024, response.year());

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
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            carService.getCarByIdForUser(carId, userId);
        });

        assertEquals("User does not own the specified car", exception.getMessage());
    }

    @Test
    void shouldRejectGetCarById_IfNotFound() {
        // Given: Car doesn't exist
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            carService.getCarByIdForUser(carId, userId);
        });
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
                new BigDecimal("450.0")
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
                new BigDecimal("400.0")
        );

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            carService.updateCar(carId, userId, updateRequest);
        });

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
        assertThrows(IllegalArgumentException.class, () -> {
            carService.deleteCar(carId, userId);
        });

        // Verify no deletion happened
        verify(carRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void shouldRejectDelete_IfNotFound() {
        // Given: Car doesn't exist
        when(carRepository.findById(carId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            carService.deleteCar(carId, userId);
        });

        // Verify no deletion happened
        verify(carRepository, never()).deleteById(any(UUID.class));
    }
}
