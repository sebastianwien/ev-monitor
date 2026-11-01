package com.evmonitor.application;

import com.evmonitor.application.consumption.ConsumptionCalculationService;
import com.evmonitor.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Community-CO₂: dieselbe Rechnung je Auto, Summe der Abschnitte, die im Zeitraum schließen. */
@ExtendWith(MockitoExtension.class)
class Co2ServiceTest {

    @Mock ConsumptionCalculationService calculationService;
    @Mock EvLogRepository evLogRepository;

    private Co2Service service;

    private final UUID sedan = UUID.randomUUID();
    private final UUID cityCar = UUID.randomUUID();
    private final LocalDateTime start = LocalDateTime.of(2025, 3, 1, 0, 0);
    private final LocalDateTime end = LocalDateTime.of(2025, 4, 1, 0, 0);

    @BeforeEach
    void setUp() {
        service = new Co2Service(calculationService, new PlausibilityProperties(), evLogRepository);
    }

    @Test
    void communitySummiertAbschnitteAllerAutosImZeitraumMitIhrerKlasse() {
        when(calculationService.gridSideKwhEstimate(any())).thenAnswer(inv -> ((EvLog) inv.getArgument(0)).getKwhCharged());
        when(evLogRepository.findAllByCarIds(any())).thenReturn(List.of(
                // Limousine: ein Abschnitt im Februar (zählt nicht), einer im März
                log(sedan, LocalDateTime.of(2025, 2, 1, 8, 0), 30, 10_000),
                log(sedan, LocalDateTime.of(2025, 2, 20, 8, 0), 30, 10_200),
                log(sedan, LocalDateTime.of(2025, 3, 10, 8, 0), 32, 10_400),
                // Kleinwagen: Anker im Februar, Abschnitt schließt im März
                log(cityCar, LocalDateTime.of(2025, 2, 25, 8, 0), 20, 50_000),
                log(cityCar, LocalDateTime.of(2025, 3, 5, 8, 0), 15, 50_100)));

        Co2Balance b = service.community(Map.of(sedan, VehicleCategory.SEDAN, cityCar, VehicleCategory.CITY_CAR), start, end);

        assertEquals(300, b.distanceKm());
        double petrol = (200 * VehicleCategory.SEDAN.getPetrolLitersPer100Km().doubleValue()
                + 100 * VehicleCategory.CITY_CAR.getPetrolLitersPer100Km().doubleValue()) / 100 * Co2Factors.PETROL_KG_CO2E_PER_LITER;
        assertEquals(petrol, b.petrolKg(), 1e-6);
        assertEquals((32 + 15) * 406 / 1000.0, b.electricityKg(), 1e-6);
    }

    @Test
    void communityOhneAutosIstNull() {
        assertNull(service.community(Map.of(), start, end));
    }

    private static EvLog log(UUID carId, LocalDateTime at, int kwh, int odometerKm) {
        return EvLog.builder()
                .id(UUID.randomUUID()).carId(carId)
                .kwhCharged(BigDecimal.valueOf(kwh)).odometerKm(odometerKm)
                .loggedAt(at).includeInStatistics(true)
                .dataSource(DataSource.USER_LOGGED).build();
    }
}
