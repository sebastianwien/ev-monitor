package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.VehicleSpecification;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The public range band on the model page: no login, IP limited, only the two range ends. */
class PublicPersonalRangeControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/public/personal-range/Volkswagen/ID.3/preview";
    private static final Map<String, Object> VALID = Map.of(
            "commuteKmPerDay", 40, "highwaySharePercent", 30, "homeCharging", true, "longTripKm", 600);

    @MockitoBean
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(rateLimitService.tryConsumePreview(any())).thenReturn(true);
        if (!vehicleSpecificationRepository.findByCarModelOrderByBatteryCapacityKwhAsc(CarBrand.CarModel.ID_3.name()).isEmpty()) return;
        vehicleSpecificationRepository.save(VehicleSpecification.createNew(
                "VW", CarBrand.CarModel.ID_3.name(), new BigDecimal("82"), new BigDecimal("77"),
                new BigDecimal("550"), new BigDecimal("15.5"),
                VehicleSpecification.WltpType.COMBINED, VehicleSpecification.RatingSource.WLTP));
    }

    @Test
    void guestGetsTheBandWithoutStoringAnything() {
        ResponseEntity<String> response = restTemplate.postForEntity(URL, json(VALID), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("minKm").contains("maxKm").contains("dataLevel")
                .doesNotContain("monthlyCostEur").doesNotContain("assumptions");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void outOfRangeInputIsRejected() {
        Map<String, Object> bad = Map.of("commuteKmPerDay", 501, "highwaySharePercent", 30,
                "homeCharging", true, "longTripKm", 600);

        ResponseEntity<String> response = restTemplate.postForEntity(URL, json(bad), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void commuteDaysZeroToSixAreAcceptedOutsideRejected() {
        for (int days : new int[] {-1, 7}) {
            ResponseEntity<String> response = restTemplate.postForEntity(URL, json(withCommuteDays(days)), String.class);
            assertThat(response.getStatusCode()).as("%d days", days).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        for (int days : new int[] {0, 6}) {
            ResponseEntity<String> response = restTemplate.postForEntity(URL, json(withCommuteDays(days)), String.class);
            assertThat(response.getStatusCode()).as("%d days", days).isEqualTo(HttpStatus.OK);
        }
    }

    private static Map<String, Object> withCommuteDays(int days) {
        return Map.of("commuteKmPerDay", 40, "highwaySharePercent", 30,
                "homeCharging", true, "longTripKm", 600, "commuteDaysPerWeek", days);
    }

    @Test
    void unknownModelIs404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/public/personal-range/Foo/Bar/preview", json(VALID), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void exhaustedIpLimitIs429() {
        when(rateLimitService.tryConsumePreview(any())).thenReturn(false);

        ResponseEntity<String> response = restTemplate.postForEntity(URL, json(VALID), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
