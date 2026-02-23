package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.DrivingStyle;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvLogServiceTest {

    @Mock
    private EvLogRepository evLogRepository;

    @Mock
    private CarRepository carRepository;

    @InjectMocks
    private EvLogService evLogService;

    @Test
    void logDrive_shouldCreateLog() {
        UUID userId = UUID.randomUUID();
        UUID carId = UUID.randomUUID();

        Car mockCar = Car.createNew(
                userId,
                "Tesla",
                "Model 3",
                2023,
                "ABC123",
                new BigDecimal("75.0"));

        EvLogRequest request = new EvLogRequest(
                carId,
                new BigDecimal("100.5"),
                new BigDecimal("15.2"),
                new BigDecimal("20.0"),
                DrivingStyle.NORMAL);

        EvLog mockedSavedLog = EvLog.createNew(
                request.carId(),
                request.distanceKm(),
                request.consumptionKwhPer100km(),
                request.outsideTempC(),
                request.drivingStyle());

        when(carRepository.findById(carId)).thenReturn(Optional.of(mockCar));
        when(evLogRepository.save(any(EvLog.class))).thenReturn(mockedSavedLog);

        EvLogResponse response = evLogService.logDrive(userId, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("100.5"), response.distanceKm());
        assertEquals(DrivingStyle.NORMAL, response.drivingStyle());
        assertEquals(carId, response.carId());
    }
}
