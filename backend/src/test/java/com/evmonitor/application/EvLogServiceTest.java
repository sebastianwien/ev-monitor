package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
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
    void logCharging_shouldCreateLog() {
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
                new BigDecimal("50.5"),  // kwhCharged
                new BigDecimal("20.25"), // costEur
                60,                       // chargeDurationMinutes
                null,
                null,
                null);

        EvLog mockedSavedLog = EvLog.createNew(
                request.carId(),
                request.kwhCharged(),
                request.costEur(),
                request.chargeDurationMinutes(),
                null,
                null);

        when(carRepository.findById(carId)).thenReturn(Optional.of(mockCar));
        when(evLogRepository.save(any(EvLog.class))).thenReturn(mockedSavedLog);

        EvLogResponse response = evLogService.logCharging(userId, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("50.5"), response.kwhCharged());
        assertEquals(new BigDecimal("20.25"), response.costEur());
        assertEquals(60, response.chargeDurationMinutes());
        assertEquals(carId, response.carId());
    }
}
