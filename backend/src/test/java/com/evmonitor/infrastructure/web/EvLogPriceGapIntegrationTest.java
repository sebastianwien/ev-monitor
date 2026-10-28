package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.*;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Weiß ich nicht mehr": der Nutzer erklärt den fehlenden Preis einer eigenen Ladung für
 * unbekannt. Danach fragt keine Stelle mehr danach (Liste, Zähler, Sammelnachtrag), die
 * Ladung bleibt aber eine Ladung ohne Preis, wer sie ausdrücklich mit anfragt, sieht sie.
 */
class EvLogPriceGapIntegrationTest extends AbstractIntegrationTest {

    private static final String GEOHASH = "u33dc1v";

    @Autowired
    private JpaUserChargingProviderRepository chargingProviderRepository;

    private User user;
    private UUID carId;

    @BeforeEach
    void setUp() {
        user = createAndSaveUser("gap-" + System.nanoTime() + "@example.com");
        carId = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3).getId();
    }

    private EvLog saveLog(BigDecimal costEur, int daysAgo) {
        LocalDateTime now = LocalDateTime.now();
        return evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID())
                .carId(carId)
                .kwhCharged(new BigDecimal("30.0"))
                .costEur(costEur)
                .geohash(GEOHASH)
                .publicCharging(true)
                .chargingType(ChargingType.DC)
                .loggedAt(now.minusDays(daysAgo))
                .includeInStatistics(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private ResponseEntity<Map> markUnknown(UUID logId, User as, String field) {
        return restTemplate.exchange("/api/logs/" + logId + "/gap", HttpMethod.PATCH,
                createAuthRequest(Map.of("field", field), as.getId(), as.getEmail()), Map.class);
    }

    private List<Map<String, Object>> priceless(String query) {
        ResponseEntity<List> res = restTemplate.exchange("/api/logs/priceless?carId=" + carId + query,
                HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), List.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        return res.getBody();
    }

    @Test
    void unknownPriceLeavesThePricelessListButStaysVisibleOnRequest() {
        EvLog open = saveLog(null, 3);
        EvLog unknown = saveLog(null, 2);

        ResponseEntity<Map> res = markUnknown(unknown.getId(), user, "PRICE");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(true, res.getBody().get("priceUnknown"));
        assertEquals(List.of(open.getId().toString()), priceless("").stream().map(l -> l.get("id")).toList());

        List<Map<String, Object>> all = priceless("&includeUnknown=true");
        assertEquals(2, all.size());
        assertEquals(true, all.get(0).get("priceUnknown"), "neueste zuerst, mit Kennzeichen");
        assertEquals(false, all.get(1).get("priceUnknown"));
    }

    @Test
    void unknownPriceIsNotCountedAtTheLocation() {
        saveLog(null, 3);
        EvLog unknown = saveLog(null, 2);
        markUnknown(unknown.getId(), user, "PRICE");

        ResponseEntity<Map> res = restTemplate.exchange("/api/logs/priceless-count?geohash=" + GEOHASH,
                HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), Map.class);

        assertEquals(1, ((Number) res.getBody().get("count")).intValue());
    }

    @Test
    void locationBackfillSkipsUnknownPrice() {
        EvLog open = saveLog(null, 3);
        EvLog unknown = saveLog(null, 2);
        markUnknown(unknown.getId(), user, "PRICE");
        UUID providerId = saveProvider();

        ResponseEntity<Map> res = restTemplate.exchange("/api/logs/apply-tariff-at-location", HttpMethod.PATCH,
                createAuthRequest(Map.of("geohash", GEOHASH, "chargingProviderId", providerId.toString()),
                        user.getId(), user.getEmail()), Map.class);

        assertEquals(1, ((Number) res.getBody().get("priced")).intValue());
        assertNotNull(evLogRepository.findById(open.getId()).orElseThrow().getCostEur());
        assertNull(evLogRepository.findById(unknown.getId()).orElseThrow().getCostEur());
    }

    @Test
    void enteringAPriceLaterClearsTheFlag() {
        EvLog unknown = saveLog(null, 2);
        markUnknown(unknown.getId(), user, "PRICE");

        EvLog marked = evLogRepository.findById(unknown.getId()).orElseThrow();
        assertTrue(marked.isPriceUnknown());
        evLogRepository.save(marked.toBuilder().costEur(new BigDecimal("12.00")).build());

        assertFalse(evLogRepository.findById(unknown.getId()).orElseThrow().isPriceUnknown());
    }

    @Test
    void foreignLogIsNotFoundAndStaysUntouched() {
        EvLog log = saveLog(null, 2);
        User stranger = createAndSaveUser("gap-stranger-" + System.nanoTime() + "@example.com");

        ResponseEntity<Map> res = markUnknown(log.getId(), stranger, "PRICE");

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertFalse(evLogRepository.findById(log.getId()).orElseThrow().isPriceUnknown());
    }

    @Test
    void logWithPriceHasNoGap() {
        EvLog priced = saveLog(new BigDecimal("15.00"), 2);

        assertEquals(HttpStatus.CONFLICT, markUnknown(priced.getId(), user, "PRICE").getStatusCode());
    }

    @Test
    void unknownFieldIsRejected() {
        EvLog log = saveLog(null, 2);

        assertEquals(HttpStatus.BAD_REQUEST, markUnknown(log.getId(), user, "COLOR").getStatusCode());
    }

    @Test
    void requiresAuthentication() {
        EvLog log = saveLog(null, 2);

        ResponseEntity<String> res = restTemplate.exchange("/api/logs/" + log.getId() + "/gap", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("field", "PRICE")), String.class);

        assertTrue(res.getStatusCode().is4xxClientError());
        assertFalse(evLogRepository.findById(log.getId()).orElseThrow().isPriceUnknown());
    }

    private UUID saveProvider() {
        UserChargingProviderEntity p = new UserChargingProviderEntity();
        p.setUserId(user.getId());
        p.setProviderName("EnBW mobility+");
        p.setAcPricePerKwh(new BigDecimal("0.3900"));
        p.setDcPricePerKwh(new BigDecimal("0.5900"));
        p.setSessionFeeEur(BigDecimal.ZERO);
        p.setMonthlyFeeEur(BigDecimal.ZERO);
        p.setActiveFrom(LocalDate.now().minusYears(1));
        return chargingProviderRepository.save(p).getId();
    }
}
