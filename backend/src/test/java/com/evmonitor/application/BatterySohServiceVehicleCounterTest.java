package com.evmonitor.application;

import com.evmonitor.domain.BatterySohEntry;
import com.evmonitor.domain.BatterySohRepository;
import com.evmonitor.domain.BatterySohSource;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.CarStatus;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * VEHICLE_COUNTER: Summe der Fahrt-Zähler durch Summe der SoC-Deltas der Polestar-Fahrten mit Zählerpfad.
 * Nur Fahrten dieser Quelle und mit {@code energyPath = COUNTER} zählen, DC-Nachlauf wird ausgeschlossen,
 * die Tore der BMS-Quelle gelten (ein Eintrag je Monat, 5 Prozentpunkte Abstand).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatterySohServiceVehicleCounterTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 9, 1, 6, 0, 0, 0, ZoneOffset.UTC);

    @Mock private CarRepository carRepository;
    @Mock private BatterySohRepository sohRepository;
    @Mock private EvLogRepository evLogRepository;
    @Mock private EvTripRepository tripRepository;

    private BatterySohService service;
    private final UUID carId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BatterySohService(sohRepository, carRepository, evLogRepository, tripRepository);
        Car car = Car.builder()
                .id(carId).userId(UUID.randomUUID())
                .model(CarBrand.CarModel.POLESTAR_3).year(2025)
                .specNetBatteryCapacityKwh(new BigDecimal("107.0"))
                .status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .registrationDate(LocalDate.of(2025, 1, 1))
                .build();
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));
        when(carRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sohRepository.findByCarId(carId)).thenReturn(List.of());
        when(evLogRepository.findAllByCarId(carId)).thenReturn(List.of());
    }

    private static EvTrip trip(UUID carId, int i, int socStart, int socEnd, String kwh, String source, String extras) {
        return EvTrip.builder()
                .id(UUID.randomUUID()).carId(carId).userId(UUID.randomUUID())
                .dataSource(source)
                .tripStartedAt(T0.plusDays(i)).tripEndedAt(T0.plusDays(i).plusHours(1))
                .socStart(BigDecimal.valueOf(socStart)).socEnd(BigDecimal.valueOf(socEnd))
                .estimatedConsumedKwh(kwh == null ? null : new BigDecimal(kwh))
                .telemetryExtras(extras)
                .status("COMPLETED")
                .build();
    }

    private static final String COUNTER = "{\"polestar\":{\"energyPath\":\"COUNTER\"}}";
    private static final String SOC = "{\"polestar\":{\"energyPath\":\"SOC\"}}";

    /** 20 Fahrten à 15 Prozentpunkte mit 1,04 kWh je Prozent: 104 kWh von 107 = 97,20 %. */
    private List<EvTrip> twentyCounterTrips() {
        List<EvTrip> trips = new ArrayList<>();
        for (int i = 0; i < 20; i++) trips.add(trip(carId, i, 80, 65, "15.6", "POLESTAR_DATA_PORTAL", COUNTER));
        return trips;
    }

    @Test
    void persistsEntryFromCounterTripsOnly() {
        List<EvTrip> trips = twentyCounterTrips();
        trips.add(trip(carId, 30, 80, 60, "12.0", "POLESTAR_DATA_PORTAL", SOC));      // SoC-Pfad: zählt nicht
        trips.add(trip(carId, 31, 80, 60, "12.0", "SMARTCAR_LIVE", COUNTER));         // fremde Quelle: zählt nicht
        when(tripRepository.findAllByCarIdAndDeletedAtIsNull(carId)).thenReturn(trips);

        service.persistVehicleCounter(carId);

        ArgumentCaptor<BatterySohEntry> captor = ArgumentCaptor.forClass(BatterySohEntry.class);
        verify(sohRepository).save(captor.capture());
        BatterySohEntry e = captor.getValue();
        assertEquals(BatterySohSource.VEHICLE_COUNTER, e.getSource());
        assertEquals(new BigDecimal("97.20"), e.getSohPercent());
        assertEquals(20, e.getSampleSize());
        assertEquals(new BigDecimal("300"), e.getSocHubPercent());
    }

    @Test
    void skipsBelowGates() {
        List<EvTrip> trips = twentyCounterTrips();
        trips.remove(0);
        when(tripRepository.findAllByCarIdAndDeletedAtIsNull(carId)).thenReturn(trips);

        service.persistVehicleCounter(carId);

        verify(sohRepository, never()).save(any());
    }

    @Test
    void excludesTripsWithinAnHourAfterDcCharge() {
        List<EvTrip> trips = twentyCounterTrips();
        // Fahrt 0 startet 30 min nach Ende einer DC-Ladung (loggedAt lokal + Dauer) und fällt raus -> nur 19 Fahrten
        LocalDateTime dcStart = T0.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().minusMinutes(90);
        EvLog dc = mock(EvLog.class);
        when(dc.getChargingType()).thenReturn(ChargingType.DC);
        when(dc.getLoggedAt()).thenReturn(dcStart);
        when(dc.getChargeDurationMinutes()).thenReturn(60);
        when(dc.getDeletedAt()).thenReturn(null);
        when(evLogRepository.findAllByCarId(carId)).thenReturn(List.of(dc));
        when(tripRepository.findAllByCarIdAndDeletedAtIsNull(carId)).thenReturn(trips);

        service.persistVehicleCounter(carId);

        verify(sohRepository, never()).save(any());
    }

    @Test
    void oneEntryPerMonthAndMaxFivePointsDeviation() {
        when(tripRepository.findAllByCarIdAndDeletedAtIsNull(carId)).thenReturn(twentyCounterTrips());
        BatterySohEntry thisMonth = BatterySohEntry.manual(UUID.randomUUID(), carId, new BigDecimal("96.00"),
                LocalDate.now(), LocalDateTime.now());
        when(sohRepository.findByCarId(carId)).thenReturn(List.of(thisMonth));
        service.persistVehicleCounter(carId);
        verify(sohRepository, never()).save(any());

        BatterySohEntry farOff = BatterySohEntry.manual(UUID.randomUUID(), carId, new BigDecimal("85.00"),
                LocalDate.now().minusMonths(2), LocalDateTime.now().minusMonths(2));
        when(sohRepository.findByCarId(carId)).thenReturn(List.of(farOff));
        service.persistVehicleCounter(carId);
        verify(sohRepository, never()).save(any());
    }

    @Test
    void hasCounterPath_readsPolestarBlock() {
        assertTrue(BatterySohService.hasCounterPath(trip(carId, 0, 80, 70, "10", "POLESTAR_DATA_PORTAL", COUNTER)));
        assertFalse(BatterySohService.hasCounterPath(trip(carId, 0, 80, 70, "10", "POLESTAR_DATA_PORTAL", SOC)));
        assertFalse(BatterySohService.hasCounterPath(trip(carId, 0, 80, 70, "10", "POLESTAR_DATA_PORTAL", null)));
        assertFalse(BatterySohService.hasCounterPath(trip(carId, 0, 80, 70, "10", "POLESTAR_DATA_PORTAL", "kein json")));
    }

    @Test
    void dataSourceEnumNameMatchesWireValue() {
        assertEquals("POLESTAR_DATA_PORTAL", DataSource.POLESTAR_DATA_PORTAL.name());
    }
}
