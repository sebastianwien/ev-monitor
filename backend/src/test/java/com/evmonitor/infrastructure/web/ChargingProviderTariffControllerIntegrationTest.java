package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingProviderTariffResponse;
import com.evmonitor.infrastructure.persistence.ChargingProviderTariffEntity;
import com.evmonitor.infrastructure.persistence.JpaChargingProviderTariffRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ChargingProviderTariffController.
 * Endpoint is public - no auth required.
 */
class ChargingProviderTariffControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JpaChargingProviderTariffRepository tariffRepository;

    @BeforeEach
    void setUp() {
        tariffRepository.deleteAll();
        tariffRepository.save(buildTariff("IONITY", "Go", "DC", new BigDecimal("0.3900"), BigDecimal.ZERO, null));
        tariffRepository.save(buildTariff("IONITY", "Direct", "DC", new BigDecimal("0.6900"), BigDecimal.ZERO, null));
        tariffRepository.save(buildTariff("EnBW", "Tariff L", "DC", new BigDecimal("0.3900"), new BigDecimal("11.99"), null));
        tariffRepository.save(buildTariff("Maingau", "Standard", "DC", new BigDecimal("0.7200"), BigDecimal.ZERO, null));
        // Expired tariff - should NOT appear
        tariffRepository.save(buildExpiredTariff("OldEMP", "Legacy", "DC", new BigDecimal("0.9900")));
    }

    @Test
    void shouldReturn200_AndTariffs_WithoutAuthentication() {
        ResponseEntity<List<ChargingProviderTariffResponse>> response = restTemplate.exchange(
                "/api/charging-provider-tariffs",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    void shouldContainExpectedEmps() {
        ResponseEntity<List<ChargingProviderTariffResponse>> response = restTemplate.exchange(
                "/api/charging-provider-tariffs",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        List<String> empNames = response.getBody().stream()
                .map(ChargingProviderTariffResponse::getEmpName).distinct().toList();

        assertTrue(empNames.contains("IONITY"));
        assertTrue(empNames.contains("EnBW"));
        assertTrue(empNames.contains("Maingau"));
    }

    @Test
    void shouldHaveCorrectIonityGoPrice() {
        ResponseEntity<List<ChargingProviderTariffResponse>> response = restTemplate.exchange(
                "/api/charging-provider-tariffs",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        ChargingProviderTariffResponse ionityGo = response.getBody().stream()
                .filter(t -> "IONITY".equals(t.getEmpName()) && "Go".equals(t.getTariffVariant()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("IONITY Go not found"));

        assertEquals(0, new BigDecimal("0.3900").compareTo(ionityGo.getPricePerKwh()));
        assertEquals(0, BigDecimal.ZERO.compareTo(ionityGo.getMonthlyFeeEur()));
    }

    @Test
    void shouldNotReturnExpiredTariffs() {
        ResponseEntity<List<ChargingProviderTariffResponse>> response = restTemplate.exchange(
                "/api/charging-provider-tariffs",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        List<String> empNames = response.getBody().stream()
                .map(ChargingProviderTariffResponse::getEmpName).toList();

        assertFalse(empNames.contains("OldEMP"), "Expired tariff should not be returned");
    }

    @Test
    void shouldReturn200_AndEmpList_WithoutAuthentication() {
        ResponseEntity<List<String>> response = restTemplate.exchange(
                "/api/charging-provider-tariffs/emps",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("IONITY"));
        assertTrue(response.getBody().contains("EnBW"));
        assertFalse(response.getBody().contains("OldEMP"), "Expired EMPs should not appear in list");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ChargingProviderTariffEntity buildTariff(String emp, String variant, String type,
                                                      BigDecimal price, BigDecimal monthlyFee, String tier) {
        ChargingProviderTariffEntity e = new ChargingProviderTariffEntity();
        e.setId(UUID.randomUUID());
        e.setEmpName(emp);
        e.setTariffVariant(variant);
        e.setChargingType(type);
        e.setPricePerKwh(price);
        e.setMonthlyFeeEur(monthlyFee);
        e.setSessionFeeEur(BigDecimal.ZERO);
        e.setPriceTier(tier);
        e.setDynamicPricing(false);
        e.setValidFrom(LocalDate.of(2025, 1, 1));
        e.setValidUntil(null);
        e.setLastVerifiedAt(LocalDateTime.now());
        return e;
    }

    private ChargingProviderTariffEntity buildExpiredTariff(String emp, String variant, String type, BigDecimal price) {
        ChargingProviderTariffEntity e = buildTariff(emp, variant, type, price, BigDecimal.ZERO, null);
        e.setValidUntil(LocalDate.of(2025, 12, 31));
        return e;
    }
}
