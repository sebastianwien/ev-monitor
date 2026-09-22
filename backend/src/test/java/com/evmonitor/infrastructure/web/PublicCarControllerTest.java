package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarShareService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ueber HTTP: Teilen, oeffentlich abrufen ohne Auth-Header, widerrufen.
 */
class PublicCarControllerTest extends AbstractIntegrationTest {

    @Autowired private CarShareService shareService;

    @Test
    @SuppressWarnings("rawtypes")
    void publicCar_isReachableWithoutAuthentication() {
        User user = createAndSaveUser("pubcar-ok-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        String token = shareService.createShare(car.getId(), user).token();

        ResponseEntity<Map> res = restTemplate.getForEntity("/api/public/car/" + token, Map.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("Tesla Model 3", res.getBody().get("carModel"));
        assertFalse(res.getBody().containsKey("userId"));
        assertFalse(res.getBody().containsKey("licensePlate"));
        assertFalse(res.getBody().containsKey("id"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void publicCar_unknownToken_is404() {
        ResponseEntity<Map> res = restTemplate.getForEntity("/api/public/car/gibtsnicht", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shareEndpoints_requireOwnership() {
        User owner = createAndSaveUser("pubcar-owner-" + System.nanoTime() + "@test.com");
        User other = createAndSaveUser("pubcar-other-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);

        ResponseEntity<Map> created = restTemplate.exchange("/api/cars/" + car.getId() + "/share",
                HttpMethod.POST, createAuthRequest(owner.getId(), owner.getEmail()), Map.class);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        assertNotNull(created.getBody().get("url"));

        ResponseEntity<Map> foreign = restTemplate.exchange("/api/cars/" + car.getId() + "/share",
                HttpMethod.POST, createAuthRequest(other.getId(), other.getEmail()), Map.class);
        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());

        ResponseEntity<Void> revoked = restTemplate.exchange("/api/cars/" + car.getId() + "/share",
                HttpMethod.DELETE, createAuthRequest(owner.getId(), owner.getEmail()), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, revoked.getStatusCode());

        String token = String.valueOf(created.getBody().get("token"));
        assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity("/api/public/car/" + token, Map.class).getStatusCode());
    }

    @Test
    void publicCarOgImage_rendersAndDiesWithRevoke() {
        User user = createAndSaveUser("pubcar-og-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        String token = shareService.createShare(car.getId(), user).token();

        ResponseEntity<byte[]> first = restTemplate.getForEntity("/api/public/car/" + token + "/og.png", byte[].class);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertTrue(first.getBody().length > 0);

        shareService.revokeShare(car.getId(), user);

        assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity("/api/public/car/" + token + "/og.png", byte[].class).getStatusCode());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void publicCarImage_onlyWhenImagePublic() {
        User user = createAndSaveUser("pubcar-img-" + System.nanoTime() + "@test.com");
        Car car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        String token = shareService.createShare(car.getId(), user).token();

        // Kein Bild hinterlegt -> 404, nie 403 (ein 403 wuerde verraten, dass ein privates Bild existiert)
        assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity("/api/public/car/" + token + "/image", byte[].class).getStatusCode());
    }
}
