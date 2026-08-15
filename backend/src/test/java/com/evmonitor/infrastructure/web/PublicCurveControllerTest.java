package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogShareService;
import com.evmonitor.application.ShareResponse;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end ueber HTTP: Teilen, oeffentlich abrufen, widerrufen.
 * Der oeffentliche Abruf laeuft bewusst ohne Auth-Header - genau so kommt ein
 * Crawler oder ein Forennutzer an.
 */
class PublicCurveControllerTest extends AbstractIntegrationTest {

    private static final String CURVE_JSON =
            "[{\"ts\":1715515200000,\"kw\":42.5,\"soc\":18.5},{\"ts\":1715515260000,\"kw\":150.0,\"soc\":24.0}]";

    @Autowired private EvLogRepository evLogRepository;
    @Autowired private EvLogShareService shareService;

    @Test
    @SuppressWarnings("rawtypes")
    void publicCurve_isReachableWithoutAuthentication() {
        User user = createAndSaveAutoSyncLiveUser("pubc-ok-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());
        String token = shareService.createShare(log.getId(), user).token();

        ResponseEntity<Map> res = restTemplate.getForEntity("/api/public/curve/" + token, Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("Tesla Model 3", res.getBody().get("carModel"));
        assertNotNull(res.getBody().get("points"));
        // Was nicht drinsteht, kann nicht leaken.
        assertFalse(res.getBody().containsKey("odometerKm"));
        assertFalse(res.getBody().containsKey("geohash"));
        assertFalse(res.getBody().containsKey("costEur"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void publicCurve_unknownToken_is404() {
        ResponseEntity<Map> res = restTemplate.getForEntity("/api/public/curve/gibtsnicht99", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void publicCurve_afterRevoke_is404() {
        User user = createAndSaveAutoSyncLiveUser("pubc-rev-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());
        String token = shareService.createShare(log.getId(), user).token();

        restTemplate.exchange("/api/logs/" + log.getId() + "/share", HttpMethod.DELETE,
                createAuthRequest(user.getId(), user.getEmail()), Void.class);

        ResponseEntity<Map> res = restTemplate.getForEntity("/api/public/curve/" + token, Map.class);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shareEndpoint_requiresAuthentication() {
        User user = createAndSaveAutoSyncLiveUser("pubc-noauth-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());

        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/logs/" + log.getId() + "/share", HttpEntity.EMPTY, Map.class);

        assertTrue(res.getStatusCode() == HttpStatus.UNAUTHORIZED || res.getStatusCode() == HttpStatus.FORBIDDEN,
                "Teilen darf nicht ohne Anmeldung gehen, war: " + res.getStatusCode());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shareEndpoint_foreignLog_is404() {
        User owner = createAndSaveAutoSyncLiveUser("pubc-own-" + System.nanoTime() + "@test.com");
        User intruder = createAndSaveAutoSyncLiveUser("pubc-intr-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());

        ResponseEntity<Map> res = restTemplate.exchange(
                "/api/logs/" + log.getId() + "/share", HttpMethod.POST,
                createAuthRequest(intruder.getId(), intruder.getEmail()), Map.class);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shareEndpoint_withoutEntitlement_is403() {
        User user = createAndSaveUser("pubc-free-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());

        ResponseEntity<Map> res = restTemplate.exchange(
                "/api/logs/" + log.getId() + "/share", HttpMethod.POST,
                createAuthRequest(user.getId(), user.getEmail()), Map.class);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void shareEndpoint_returnsUsableUrl() {
        User user = createAndSaveAutoSyncLiveUser("pubc-url-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLogWithCurve(car.getId());

        ResponseEntity<ShareResponse> res = restTemplate.exchange(
                "/api/logs/" + log.getId() + "/share", HttpMethod.POST,
                createAuthRequest(user.getId(), user.getEmail()), ShareResponse.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().url().contains("/ladekurve/"));
        assertTrue(res.getBody().url().endsWith(res.getBody().token()));
    }

    private EvLog saveLogWithCurve(UUID carId) {
        EvLog log = EvLog.createFromInternal(
                carId, new BigDecimal("43.8"), 17, null,
                LocalDateTime.now().minusHours(1), null, null,
                DataSource.TESLA_LIVE, null, null,
                null, null, null, null, null, null, null);
        EvLog saved = evLogRepository.save(log);
        evLogRepository.updatePowerCurvePoints(saved.getId(), CURVE_JSON);
        return saved;
    }
}
