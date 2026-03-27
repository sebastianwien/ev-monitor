package com.evmonitor.infrastructure.web;

import com.evmonitor.application.TaxExportService;
import com.evmonitor.domain.*;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaxExportControllerTest extends AbstractIntegrationTest {

    private User user;
    private Car businessCar;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("tax-export-test-" + System.nanoTime() + "@example.com");
        Car car = TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(75.0));
        businessCar = carRepository.save(car.withBusinessCar(true));
    }

    @Test
    void preview_returnsSessionCountAndTotals_forHomeChargingSessions() {
        // Two home sessions in January 2026
        evLogRepository.save(TestDataBuilder.createTestEvLogWithTimestamp(
                businessCar.getId(), new BigDecimal("12.5"), null,
                LocalDateTime.of(2026, 1, 5, 18, 0)));
        evLogRepository.save(TestDataBuilder.createTestEvLogWithTimestamp(
                businessCar.getId(), new BigDecimal("8.3"), null,
                LocalDateTime.of(2026, 1, 15, 7, 30)));

        var request = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tax-export/preview?carId=" + businessCar.getId()
                        + "&from=2026-01-01&to=2026-01-31&usePauschale=true",
                org.springframework.http.HttpMethod.GET, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().get("sessionCount"));

        BigDecimal expectedKwh = new BigDecimal("20.8");
        BigDecimal expectedCost = expectedKwh.multiply(TaxExportService.BMF_PAUSCHALE_2026)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedCost.doubleValue(),
                ((Number) response.getBody().get("totalCostEur")).doubleValue(), 0.001);
    }

    @Test
    void preview_excludesPublicChargingSessions() {
        // One home session and one public session
        evLogRepository.save(TestDataBuilder.createTestEvLogWithTimestamp(
                businessCar.getId(), new BigDecimal("10.0"), null,
                LocalDateTime.of(2026, 2, 10, 12, 0)));

        // Public session - create via EvLog.createNew with isPublicCharging=true
        EvLog publicSession = EvLog.createNew(
                businessCar.getId(), new BigDecimal("25.0"), null,
                30, "u33db", 50000, null, 80,
                LocalDateTime.of(2026, 2, 12, 14, 0),
                ChargingType.DC, null, null, true, "EnBW");
        evLogRepository.save(publicSession);

        var request = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tax-export/preview?carId=" + businessCar.getId()
                        + "&from=2026-02-01&to=2026-02-28&usePauschale=true",
                org.springframework.http.HttpMethod.GET, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("sessionCount"), "Only home session should be counted");
    }

    @Test
    void preview_returnsZeroSessions_forEmptyPeriod() {
        var request = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tax-export/preview?carId=" + businessCar.getId()
                        + "&from=2020-01-01&to=2020-01-31&usePauschale=true",
                org.springframework.http.HttpMethod.GET, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().get("sessionCount"));
    }

    @Test
    void preview_rejectsNonBusinessCar() {
        Car privateCar = carRepository.save(
                TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_Y, BigDecimal.valueOf(75.0)));

        var request = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/tax-export/preview?carId=" + privateCar.getId()
                        + "&from=2026-01-01&to=2026-01-31&usePauschale=true",
                org.springframework.http.HttpMethod.GET, request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void preview_rejectsCarOwnedByAnotherUser() {
        User otherUser = createAndSaveUser("tax-other-" + System.nanoTime() + "@example.com");
        Car otherCar = carRepository.save(
                TestDataBuilder.createTestCar(otherUser.getId(), CarBrand.CarModel.MODEL_Y, BigDecimal.valueOf(77.0))
                        .withBusinessCar(true));

        var request = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/tax-export/preview?carId=" + otherCar.getId()
                        + "&from=2026-01-01&to=2026-01-31&usePauschale=true",
                org.springframework.http.HttpMethod.GET, request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void csvDownload_usesBmfPauschaleForCostCalculation() {
        evLogRepository.save(TestDataBuilder.createTestEvLogWithTimestamp(
                businessCar.getId(), new BigDecimal("10.0"), new BigDecimal("5.00"),
                LocalDateTime.of(2026, 3, 5, 20, 0)));

        var request = createAuthRequest(user.getId(), user.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/tax-export/csv?carId=" + businessCar.getId()
                        + "&from=2026-03-01&to=2026-03-31&usePauschale=true",
                org.springframework.http.HttpMethod.GET, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Datum"), "CSV should contain header");
        // BMF: 10 kWh * 0.3436 = 3.44 EUR (not original 5.00)
        assertTrue(response.getBody().contains("3.44"), "CSV must use BMF Pauschale, not original costEur");
        assertTrue(response.getBody().contains("TEST-123"), "CSV should contain license plate");
    }
}
