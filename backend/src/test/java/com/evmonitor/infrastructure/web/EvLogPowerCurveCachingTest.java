package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PowerCurveResponse;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: GET /api/logs/{id}/power-curve haengte den ETag nur an die Log-ID.
 * Ein Client, der die Kurve gesehen hatte, solange sie noch unvollstaendig war,
 * bekam nach dem Nachschreiben der vollen Kurve auf jede Revalidierung 304 und
 * blieb dauerhaft auf dem alten Stand - waehrend das serverseitig gerenderte
 * Share-Bild laengst richtig war.
 *
 * Der ETag muss den Inhalt widerspiegeln, und der Client muss ueberhaupt
 * revalidieren duerfen (kein tagelanges max-age).
 */
class EvLogPowerCurveCachingTest extends AbstractIntegrationTest {

    private static final String SHORT_CURVE = "[{\"ts\":1715515200000,\"kw\":62.0,\"soc\":80.0}]";
    private static final String FULL_CURVE =
            "[{\"ts\":1715515200000,\"kw\":250.0,\"soc\":9.0},{\"ts\":1715517480000,\"kw\":50.0,\"soc\":81.0}]";

    @Test
    void etagChangesWhenCurveChanges_soStaleClientGetsFreshBody() {
        User user = createAndSaveAutoSyncLiveUser("pc-etag-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLog(car.getId());
        evLogRepository.updatePowerCurvePoints(log.getId(), SHORT_CURVE);

        ResponseEntity<PowerCurveResponse> first = get(log.getId(), user, null);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        String staleEtag = first.getHeaders().getETag();
        assertNotNull(staleEtag);
        assertEquals(1, first.getBody().points().size());

        // Connector schreibt die vollstaendige Kurve nach.
        evLogRepository.replacePowerCurvePoints(log.getId(), FULL_CURVE);

        ResponseEntity<PowerCurveResponse> revalidated = get(log.getId(), user, staleEtag);
        assertEquals(HttpStatus.OK, revalidated.getStatusCode(),
                "geaenderte Kurve darf nicht mit 304 auf den alten Client-Cache verweisen");
        assertNotEquals(staleEtag, revalidated.getHeaders().getETag());
        assertEquals(2, revalidated.getBody().points().size());
        assertEquals(250.0, revalidated.getBody().points().get(0).kw());
    }

    @Test
    void unchangedCurve_stillRevalidatesCheaplyWith304() {
        User user = createAndSaveAutoSyncLiveUser("pc-304-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLog(car.getId());
        evLogRepository.updatePowerCurvePoints(log.getId(), FULL_CURVE);

        String etag = get(log.getId(), user, null).getHeaders().getETag();

        ResponseEntity<PowerCurveResponse> again = get(log.getId(), user, etag);
        assertEquals(HttpStatus.NOT_MODIFIED, again.getStatusCode());
    }

    @Test
    void cacheControlForcesRevalidation() {
        User user = createAndSaveAutoSyncLiveUser("pc-cc-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = saveLog(car.getId());
        evLogRepository.updatePowerCurvePoints(log.getId(), FULL_CURVE);

        String cacheControl = get(log.getId(), user, null).getHeaders().getCacheControl();

        assertNotNull(cacheControl);
        assertTrue(cacheControl.contains("no-cache"),
                "ohne no-cache revalidiert der Browser tagelang nicht: " + cacheControl);
        assertTrue(cacheControl.contains("private"), cacheControl);
    }

    private ResponseEntity<PowerCurveResponse> get(UUID logId, User user, String ifNoneMatch) {
        HttpHeaders headers = createAuthHeaders(user.getId(), user.getEmail());
        if (ifNoneMatch != null) headers.setIfNoneMatch(ifNoneMatch);
        return restTemplate.exchange("/api/logs/" + logId + "/power-curve",
                HttpMethod.GET, new HttpEntity<>(headers), PowerCurveResponse.class);
    }

    private EvLog saveLog(UUID carId) {
        EvLog log = EvLog.createFromInternal(
                carId, new BigDecimal("10.0"), 30, null, LocalDateTime.now().minusHours(1),
                null, null, DataSource.TESLA_LIVE,
                null, null, null, null, null, null, null, null, null);
        return evLogRepository.save(log);
    }
}
