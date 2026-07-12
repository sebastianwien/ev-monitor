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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP contract for the retroactive "apply this card's tariff to every priceless charge here" flow.
 *
 * Two entry points exist by design:
 *  - editing a stored log: the client only has the log's geohash (lat/lon are never persisted)
 *  - creating a new log:   the client has lat/lon, the geohash is derived server-side
 */
class EvLogApplyTariffControllerTest extends AbstractIntegrationTest {

    private static final double LAT = 52.5;
    private static final double LON = 13.4;
    /** Geohash-7 of (LAT, LON) - the precision the app stores public chargers with. */
    private static final String PUBLIC_GEOHASH =
            ch.hsr.geohash.GeoHash.withCharacterPrecision(LAT, LON, 7).toBase32();

    @Autowired
    private JpaUserChargingProviderRepository chargingProviderRepository;

    private User user;
    private UUID carId;
    private UUID providerId;

    @BeforeEach
    void setUpTestData() {
        user = createAndSaveUser("tariff-ctrl-" + System.nanoTime() + "@example.com");
        carId = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3).getId();
        providerId = saveProvider(user.getId(), new BigDecimal("0.3900"), new BigDecimal("0.5900"));
    }

    private UUID saveProvider(UUID owner, BigDecimal ac, BigDecimal dc) {
        UserChargingProviderEntity p = new UserChargingProviderEntity();
        p.setUserId(owner);
        p.setProviderName("EnBW mobility+");
        p.setAcPricePerKwh(ac);
        p.setDcPricePerKwh(dc);
        p.setSessionFeeEur(BigDecimal.ZERO);
        p.setMonthlyFeeEur(BigDecimal.ZERO);
        p.setActiveFrom(LocalDate.now().minusYears(1));
        return chargingProviderRepository.save(p).getId();
    }

    private EvLog savePricelessDcLog(UUID targetCarId, String geohash) {
        return evLogRepository.save(EvLog.createNew(targetCarId, new BigDecimal("50.0"), null,
                45, geohash, 10_000, new BigDecimal("150.0"), new BigDecimal("80.0"),
                LocalDateTime.now().minusDays(2), ChargingType.DC, null, null, true, null));
    }

    private BigDecimal costOf(EvLog log) {
        return evLogRepository.findById(log.getId()).orElseThrow().getCostEur();
    }

    // ---------- counting ----------

    @Test
    void countsPricelessLogsByStoredGeohash() {
        savePricelessDcLog(carId, PUBLIC_GEOHASH);
        savePricelessDcLog(carId, PUBLIC_GEOHASH);

        ResponseEntity<Map> res = restTemplate.exchange(
                "/api/logs/priceless-count?geohash=" + PUBLIC_GEOHASH,
                HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(2, ((Number) res.getBody().get("count")).intValue());
    }

    @Test
    void countsPricelessLogsByLatLonWhenCreatingANewLog() {
        savePricelessDcLog(carId, PUBLIC_GEOHASH);

        ResponseEntity<Map> res = restTemplate.exchange(
                "/api/logs/priceless-count?lat=" + LAT + "&lon=" + LON + "&isPublic=true",
                HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, ((Number) res.getBody().get("count")).intValue(),
                "lat/lon must resolve to the same geohash the log was stored with");
    }

    @Test
    void rejectsCountWithoutAnyLocation() {
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/logs/priceless-count",
                HttpMethod.GET, createAuthRequest(user.getId(), user.getEmail()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    void countRequiresAuthentication() {
        ResponseEntity<String> res = restTemplate.exchange(
                "/api/logs/priceless-count?geohash=" + PUBLIC_GEOHASH,
                HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertTrue(res.getStatusCode().is4xxClientError());
    }

    // ---------- applying ----------

    @Test
    void appliesTariffToEveryPricelessLogAtTheGeohash() {
        EvLog a = savePricelessDcLog(carId, PUBLIC_GEOHASH);
        EvLog b = savePricelessDcLog(carId, PUBLIC_GEOHASH);

        ResponseEntity<Map> res = restTemplate.exchange(
                "/api/logs/apply-tariff-at-location", HttpMethod.PATCH,
                createAuthRequest(Map.of("geohash", PUBLIC_GEOHASH, "chargingProviderId", providerId.toString()),
                        user.getId(), user.getEmail()),
                Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(2, ((Number) res.getBody().get("priced")).intValue());
        // 50 kWh * 0.59 DC = 29.50
        assertEquals(0, new BigDecimal("29.50").compareTo(costOf(a)));
        assertEquals(0, new BigDecimal("29.50").compareTo(costOf(b)));
    }

    @Test
    void neverTouchesLogsOfAnotherUserAtTheSameGeohash() {
        User stranger = createAndSaveUser("stranger-" + System.nanoTime() + "@example.com");
        UUID strangerCar = createAndSaveCar(stranger.getId(), CarBrand.CarModel.MODEL_3).getId();
        EvLog mine = savePricelessDcLog(carId, PUBLIC_GEOHASH);
        EvLog theirs = savePricelessDcLog(strangerCar, PUBLIC_GEOHASH);

        ResponseEntity<Map> res = restTemplate.exchange(
                "/api/logs/apply-tariff-at-location", HttpMethod.PATCH,
                createAuthRequest(Map.of("geohash", PUBLIC_GEOHASH, "chargingProviderId", providerId.toString()),
                        user.getId(), user.getEmail()),
                Map.class);

        assertEquals(1, ((Number) res.getBody().get("priced")).intValue());
        assertNotNull(costOf(mine));
        assertNull(costOf(theirs), "A shared public charger must not leak pricing onto another user's log");
    }

    @Test
    void rejectsAChargingCardOwnedByAnotherUser() {
        User stranger = createAndSaveUser("stranger-" + System.nanoTime() + "@example.com");
        UUID foreignProvider = saveProvider(stranger.getId(), new BigDecimal("0.10"), new BigDecimal("0.10"));
        EvLog mine = savePricelessDcLog(carId, PUBLIC_GEOHASH);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/logs/apply-tariff-at-location", HttpMethod.PATCH,
                createAuthRequest(Map.of("geohash", PUBLIC_GEOHASH, "chargingProviderId", foreignProvider.toString()),
                        user.getId(), user.getEmail()),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertNull(costOf(mine), "A rejected request must not price anything");
    }

    @Test
    void rejectsApplyWithoutProviderOrLocation() {
        ResponseEntity<String> noProvider = restTemplate.exchange(
                "/api/logs/apply-tariff-at-location", HttpMethod.PATCH,
                createAuthRequest(Map.of("geohash", PUBLIC_GEOHASH), user.getId(), user.getEmail()),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, noProvider.getStatusCode());

        ResponseEntity<String> noLocation = restTemplate.exchange(
                "/api/logs/apply-tariff-at-location", HttpMethod.PATCH,
                createAuthRequest(Map.of("chargingProviderId", providerId.toString()), user.getId(), user.getEmail()),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, noLocation.getStatusCode());
    }

    @Test
    void applyRequiresAuthentication() {
        EvLog mine = savePricelessDcLog(carId, PUBLIC_GEOHASH);

        ResponseEntity<String> res = restTemplate.exchange(
                "/api/logs/apply-tariff-at-location", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("geohash", PUBLIC_GEOHASH, "chargingProviderId", providerId.toString())),
                String.class);

        assertTrue(res.getStatusCode().is4xxClientError());
        assertNull(costOf(mine));
    }

    @Test
    void isIdempotent_secondCallPricesNothingMore() {
        EvLog a = savePricelessDcLog(carId, PUBLIC_GEOHASH);
        HttpEntity<Map<String, String>> req = createAuthRequest(
                Map.of("geohash", PUBLIC_GEOHASH, "chargingProviderId", providerId.toString()),
                user.getId(), user.getEmail());

        restTemplate.exchange("/api/logs/apply-tariff-at-location", HttpMethod.PATCH, req, Map.class);
        ResponseEntity<Map> second = restTemplate.exchange(
                "/api/logs/apply-tariff-at-location", HttpMethod.PATCH, req, Map.class);

        assertEquals(0, ((Number) second.getBody().get("priced")).intValue());
        assertEquals(0, new BigDecimal("29.50").compareTo(costOf(a)), "Cost must stay at the first result");
    }
}
