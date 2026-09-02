package com.evmonitor.application;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * getPricelessLogs speist den "X Ladungen ohne Preis"-Banner: es liefert alle Logs eines Autos
 * ohne cost_eur (server-seitig ueber ALLE Logs, nicht nur die geladene Feed-Seite), damit auch
 * alte, weit unten liegende Ladungen auffindbar bleiben.
 */
@ExtendWith(MockitoExtension.class)
class EvLogStatisticsServicePricelessTest {

    @Mock EvLogRepository evLogRepository;
    @Mock CarRepository carRepository;
    @InjectMocks EvLogStatisticsService service;

    private final UUID carId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void getPricelessLogs_mapsPricelessQueryResult() {
        Car car = mock(Car.class);
        when(car.getUserId()).thenReturn(userId);
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        // Filtern (cost_eur IS NULL) + Sortierung uebernimmt der Repo-Query; der Service reicht durch.
        EvLog freeNew = log(null, LocalDateTime.parse("2026-03-01T10:00"));
        EvLog freeOld = log(null, LocalDateTime.parse("2026-02-01T10:00"));
        when(evLogRepository.findPricelessByCarId(carId)).thenReturn(List.of(freeNew, freeOld));

        List<EvLogResponse> res = service.getPricelessLogs(carId, userId);

        assertEquals(2, res.size());
        assertNull(res.get(0).costEur());
        assertEquals(freeNew.getId(), res.get(0).id());
        assertEquals(freeOld.getId(), res.get(1).id());
    }

    @Test
    void getPricelessLogs_throwsWhenNotOwner() {
        Car car = mock(Car.class);
        when(car.getUserId()).thenReturn(UUID.randomUUID()); // ein anderer User
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        assertThrows(IllegalArgumentException.class, () -> service.getPricelessLogs(carId, userId));
    }

    private EvLog log(BigDecimal costEur, LocalDateTime loggedAt) {
        return EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(new BigDecimal("20.0"))
                .costEur(costEur)
                .loggedAt(loggedAt)
                .dataSource(DataSource.WALLBOX_GOE)
                .includeInStatistics(true)
                .chargingType(ChargingType.AC)
                .createdAt(loggedAt)
                .updatedAt(loggedAt)
                .build();
    }
}
