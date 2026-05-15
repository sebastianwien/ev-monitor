package com.evmonitor.application;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the price-suggestion path with realistic AC charging efficiency.
 *
 * The base test profile sets ac/dc efficiency to 1.0 so formula-math tests stay readable.
 * That hides whether cost auto-fill and the GET /api/logs/price-suggestion endpoint
 * inflate prices by the AC-loss factor. This class overrides the property to 0.90 / 0.95
 * (production defaults) and pins the netto contract:
 *
 *   When a log has only kwhAtVehicle (no kwhCharged), cost_eur is netto x tariff and the
 *   price-suggestion endpoint returns cost_eur / netto - the tariff price the user set,
 *   not the grid-equivalent inflated by ~10%.
 */
@TestPropertySource(properties = {
        "evmonitor.consumption.plausibility.ac-charging-efficiency=0.90",
        "evmonitor.consumption.plausibility.dc-charging-efficiency=0.95"
})
class PriceSuggestionEfficiencyIntegrationTest extends AbstractIntegrationTest {

    private static final String VALID_TOKEN = "test-internal-token";

    @Autowired
    private JpaUserChargingProviderRepository chargingProviderRepository;

    @Autowired
    private PlausibilityProperties plausibility;

    private User testUser;
    private Car testCar;

    @BeforeEach
    void setUp() {
        testUser = createAndSaveUser("price-eff-" + System.nanoTime() + "@example.com");
        testCar = createAndSaveCar(testUser.getId(), CarBrand.CarModel.MODEL_3);
    }

    @Test
    void sanityCheck_propertyOverrideTakesEffect() {
        // Guard against silently grouping back into the default test profile (efficiency=1.0).
        assertEquals(0.90, plausibility.getAcChargingEfficiency(), 1e-9,
                "TestPropertySource override must apply - else the netto vs brutto assertions are meaningless");
    }

    @Test
    void applyPriceSuggestion_nettoOnlyAcLog_writesNettoTimesTariffNotBruttoTimesTariff() {
        // Arrange: AC home tariff 0,30 EUR/kWh, previous log at same geohash with provider
        UserChargingProviderEntity provider = new UserChargingProviderEntity();
        provider.setUserId(testUser.getId());
        provider.setProviderName("Zuhause");
        provider.setAcPricePerKwh(new BigDecimal("0.3000"));
        provider.setMonthlyFeeEur(BigDecimal.ZERO);
        provider.setSessionFeeEur(BigDecimal.ZERO);
        provider.setActiveFrom(LocalDate.now().minusDays(30));
        UserChargingProviderEntity savedProvider = chargingProviderRepository.save(provider);

        String geohash = "u1r4nt7";
        EvLog previousLog = EvLog.createFromInternal(
                testCar.getId(), new BigDecimal("12.0"), 120, geohash,
                LocalDateTime.now().minusDays(2), null, null,
                DataSource.SMARTCAR_LIVE, new BigDecimal("3.60"), ChargingType.AC,
                60000, new BigDecimal("20"), new BigDecimal("90"), null, null)
                .toBuilder().chargingProviderId(savedProvider.getId()).build();
        evLogRepository.save(previousLog);

        // Act: new SMARTCAR_LIVE log at same geohash with 13.83 kWh netto, no costEur
        // 13.83 / 2h = 6.9 kW avg -> AC inferred
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("carId", testCar.getId().toString());
        request.put("userId", testUser.getId().toString());
        request.put("kwhCharged", "13.83");
        request.put("chargeDurationMinutes", 120);
        request.put("loggedAt", LocalDateTime.now().minusHours(1).toString());
        request.put("geohash", geohash);
        request.put("dataSource", "SMARTCAR_LIVE");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/internal/logs", HttpMethod.POST,
                new HttpEntity<>(request, internalHeaders(VALID_TOKEN)), Map.class);

        // Assert: cost_eur must be netto x tariff = 13.83 * 0.30 = 4.15
        // (NOT brutto x tariff = 13.83 / 0.90 * 0.30 = 4.61)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("costEur"));
        BigDecimal actualCost = new BigDecimal(body.get("costEur").toString());
        assertEquals(0, actualCost.compareTo(new BigDecimal("4.15")),
                "cost_eur should be netto x tariff (4.15) regardless of AC efficiency pauschale, but was " + actualCost);
    }

    @Test
    void getPriceSuggestion_dividesByNettoNotBrutto_returnsTariffPriceTheUserSet() {
        // Arrange: prior netto-only AC log with cost = netto x tariff
        // (= the state after applyPriceSuggestion has been fixed; matches what users see in DB today
        //  because they overwrote the BE's wrong 4.61 with the FE's 4.15 via the edit dialog)
        // Use a real lat/lon and the same GeoHash lib the service uses, to guarantee the
        // 6-char prefix lookup matches.
        double lat = 53.0793;
        double lon = 8.8017;
        String geohash = ch.hsr.geohash.GeoHash.withCharacterPrecision(lat, lon, 7).toBase32();

        EvLog previousLog = EvLog.createFromInternal(
                testCar.getId(), new BigDecimal("13.83"), 120, geohash,
                LocalDateTime.now().minusDays(1), null, null,
                DataSource.SMARTCAR_LIVE, new BigDecimal("4.15"), ChargingType.AC,
                60001, new BigDecimal("20"), new BigDecimal("80"), null, null);
        evLogRepository.save(previousLog);

        // Act: ask the endpoint for a price suggestion at the same geohash
        HttpHeaders headers = createAuthHeaders(testUser.getId(), testUser.getEmail());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/logs/price-suggestion?lat=" + lat + "&lon=" + lon + "&isPublic=false",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        // Assert: endpoint must return 0.30 (= 4.15 / 13.83 netto), NOT 0.27 (= 4.15 / 15.37 brutto)
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "endpoint must find the prior log via 6-char geohash prefix lookup");
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        BigDecimal suggestedPrice = new BigDecimal(body.get("costPerKwh").toString());
        assertEquals(0, suggestedPrice.setScale(2, java.math.RoundingMode.HALF_UP).compareTo(new BigDecimal("0.30")),
                "price-suggestion should return tariff price (0.30) by dividing by netto, but was " + suggestedPrice);
    }

    private HttpHeaders internalHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
