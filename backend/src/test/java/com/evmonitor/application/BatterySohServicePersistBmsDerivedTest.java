package com.evmonitor.application;

import com.evmonitor.domain.BatterySohEntry;
import com.evmonitor.domain.BatterySohRepository;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.CarStatus;
import com.evmonitor.domain.EvLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression-Test fuer den SoH-Bug: BatterySohService berechnete SoH frueher
 * gegen car.batteryCapacityKwh (User-Eingabe), obwohl der Spec-Netto-Wert
 * vorhanden war. Bei einem Enyaq 85 (Brutto-Eingabe 82, Spec-Netto 77) und
 * einer BMS-Messung von 74 kWh fuehrte das zu 90.24% SoH statt korrekt 96.10%.
 */
@ExtendWith(MockitoExtension.class)
class BatterySohServicePersistBmsDerivedTest {

    @Mock private CarRepository carRepository;
    @Mock private BatterySohRepository sohRepository;
    @Mock private EvLogRepository evLogRepository;

    private BatterySohService service;

    @BeforeEach
    void setUp() {
        service = new BatterySohService(sohRepository, carRepository, evLogRepository);
    }

    private Car car(UUID id, BigDecimal customNet, BigDecimal specNet) {
        return Car.builder()
                .id(id).userId(UUID.randomUUID())
                .model(CarBrand.CarModel.ENYAQ).year(2024)
                .customNetCapacityKwh(customNet)
                .specNetBatteryCapacityKwh(specNet)
                .status(CarStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .registrationDate(LocalDate.of(2024, 1, 1))
                .build();
    }

    @Test
    void usesSpecNet_notCustomNet_forSohCalculation() {
        UUID carId = UUID.randomUUID();
        // Enyaq 85: User typte historisch Brutto 82 (customNet), Spec liefert Netto 77 (specNet).
        // BMS meldet 74 kWh aktuell verfuegbare Kapazitaet -> 74/77 = 96.10% (nicht 74/82 = 90.24%).
        when(carRepository.findById(carId)).thenReturn(Optional.of(
                car(carId, new BigDecimal("82.0"), new BigDecimal("77.0"))));
        when(sohRepository.findByCarId(carId)).thenReturn(List.of());

        service.persistBmsDerived(carId, new BigDecimal("74.0"));

        ArgumentCaptor<BatterySohEntry> captor = ArgumentCaptor.forClass(BatterySohEntry.class);
        verify(sohRepository).save(captor.capture());
        assertEquals(new BigDecimal("96.10"), captor.getValue().getSohPercent());
    }

    @Test
    void fallsBackToCustomNet_whenNoSpecLinked() {
        UUID carId = UUID.randomUUID();
        // Car ohne Spec-Link: customNet=75 ist einzige Basis. 70/75 = 93.33%.
        when(carRepository.findById(carId)).thenReturn(Optional.of(
                car(carId, new BigDecimal("75.0"), null)));
        when(sohRepository.findByCarId(carId)).thenReturn(List.of());

        service.persistBmsDerived(carId, new BigDecimal("70.0"));

        ArgumentCaptor<BatterySohEntry> captor = ArgumentCaptor.forClass(BatterySohEntry.class);
        verify(sohRepository).save(captor.capture());
        assertEquals(new BigDecimal("93.33"), captor.getValue().getSohPercent());
    }

    @Test
    void skipsSilently_whenBothCapacitiesNull() {
        UUID carId = UUID.randomUUID();
        when(carRepository.findById(carId)).thenReturn(Optional.of(car(carId, null, null)));

        service.persistBmsDerived(carId, new BigDecimal("70.0"));

        verify(sohRepository, never()).save(any());
    }

    @Test
    void skipsSilently_whenNominalNetIsZero() {
        UUID carId = UUID.randomUUID();
        // Historisches 0 in der DB darf nicht zu ArithmeticException fuehren.
        when(carRepository.findById(carId)).thenReturn(Optional.of(
                car(carId, BigDecimal.ZERO, null)));

        service.persistBmsDerived(carId, new BigDecimal("70.0"));

        verify(sohRepository, never()).save(any());
    }
}
