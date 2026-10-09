package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalRangeControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/personal-range/Volkswagen/ID.3";
    private static final Map<String, Object> VALID = Map.of(
            "commuteKmPerDay", 40, "highwaySharePercent", 30, "homeCharging", true, "longTripKm", 600);

    @BeforeEach
    void wltpSpec() {
        if (!vehicleSpecificationRepository.findByCarModelOrderByBatteryCapacityKwhAsc(CarBrand.CarModel.ID_3.name()).isEmpty()) return;
        vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                "VW", CarBrand.CarModel.ID_3.name(), new BigDecimal("82"), new BigDecimal("77"),
                new BigDecimal("550"), new BigDecimal("15.5"),
                VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.WLTP));
    }

    @Test
    void guestGets401() {
        ResponseEntity<String> response = restTemplate.postForEntity(URL, json(VALID, null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loggedInUserGetsResultAndProfileIsStored() {
        String token = tokenFor(createAndSaveUser("range-" + System.nanoTime() + "@example.com"));

        ResponseEntity<String> response = restTemplate.postForEntity(URL, json(VALID, token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("winterRangeKm").contains("dataLevel").contains("assumptions");

        ResponseEntity<String> profile = restTemplate.exchange("/api/personal-range/profile", HttpMethod.GET,
                json(null, token), String.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody()).contains("\"commuteKmPerDay\":40").contains("\"longTripKm\":600");
    }

    @Test
    void profileIsEmptyBeforeFirstCalculation() {
        String token = tokenFor(createAndSaveUser("range-empty-" + System.nanoTime() + "@example.com"));

        ResponseEntity<String> profile = restTemplate.exchange("/api/personal-range/profile", HttpMethod.GET,
                json(null, token), String.class);

        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void outOfRangeInputIsRejected() {
        String token = tokenFor(createAndSaveUser("range-bad-" + System.nanoTime() + "@example.com"));
        Map<String, Object> bad = Map.of("commuteKmPerDay", 501, "highwaySharePercent", 101,
                "homeCharging", true, "longTripKm", 49);

        ResponseEntity<String> response = restTemplate.postForEntity(URL, json(bad, token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownModelIs404() {
        String token = tokenFor(createAndSaveUser("range-404-" + System.nanoTime() + "@example.com"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/personal-range/Foo/Bar", json(VALID, token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpEntity<Object> json(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(UserPrincipal.create(user));
    }
}
