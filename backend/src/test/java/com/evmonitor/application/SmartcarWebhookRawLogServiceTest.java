package com.evmonitor.application;

import com.evmonitor.domain.SmartcarWebhookRawLog;
import com.evmonitor.domain.SmartcarWebhookRawLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartcarWebhookRawLogServiceTest {

    @Mock
    SmartcarWebhookRawLogRepository repo;

    @InjectMocks
    SmartcarWebhookRawLogService service;

    @Test
    void log_savesEntityWithAllFields() {
        InternalSmartcarWebhookLogRequest request = new InternalSmartcarWebhookLogRequest(
                "event-123", "vehicle-456", "Tesla", "Model 3", 2020,
                "[{\"type\":\"SIGNAL_UPDATED\"}]", "[{\"code\":\"odometer-traveleddistance\"}]",
                78, new BigDecimal("78432.0"), "u2ey3d7q", new BigDecimal("14.5"), "LIVE"
        );

        service.log(request);

        ArgumentCaptor<SmartcarWebhookRawLog> captor = ArgumentCaptor.forClass(SmartcarWebhookRawLog.class);
        verify(repo).save(captor.capture());
        SmartcarWebhookRawLog saved = captor.getValue();
        assertEquals("event-123", saved.getEventId());
        assertEquals("vehicle-456", saved.getSmartcarVehicleId());
        assertEquals("Tesla", saved.getMake());
        assertEquals("Model 3", saved.getModel());
        assertEquals(2020, saved.getYear());
        assertEquals(78, saved.getSocPercent());
        assertEquals(new BigDecimal("78432.0"), saved.getOdometerKm());
        assertEquals("u2ey3d7q", saved.getLocationGeohash());
        assertEquals(new BigDecimal("14.5"), saved.getOutsideTempCelsius());
        assertEquals("LIVE", saved.getMode());
    }

    @Test
    void log_withNullableFields_savesWithoutException() {
        InternalSmartcarWebhookLogRequest request = new InternalSmartcarWebhookLogRequest(
                "event-456", "vehicle-789", null, null, null,
                "[]", "[]",
                null, null, null, null, null
        );

        assertDoesNotThrow(() -> service.log(request));
        verify(repo).save(any());
    }

    @Test
    void log_duplicateEventId_swallowsExceptionWithoutThrowing() {
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        InternalSmartcarWebhookLogRequest request = new InternalSmartcarWebhookLogRequest(
                "event-dup", "vehicle-456", "Tesla", "Model 3", 2020,
                "[]", "[]", null, null, null, null, "LIVE"
        );

        assertDoesNotThrow(() -> service.log(request));
    }
}
