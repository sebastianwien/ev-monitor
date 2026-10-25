package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingBenchmarkCache;
import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.ChargingNetworkEntity;
import com.evmonitor.infrastructure.persistence.JpaChargingNetworkRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /api/charging-benchmarks: only for signed-in drivers, only AC or DC, nothing (204) when
 * fewer than five other drivers charged there. The operator gets a name of its own per test,
 * all integration tests share one database.
 */
class ChargingBenchmarkControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired JpaChargingNetworkRepository networkRepository;
    @Autowired CpoNameNormalizer cpoNameNormalizer;
    @Autowired CacheManager cacheManager;

    private String operator;
    private User me;

    @BeforeEach
    void setUp() {
        operator = "Netz-" + UUID.randomUUID().toString().substring(0, 8);
        ChargingNetworkEntity network = new ChargingNetworkEntity();
        network.setName(operator);
        networkRepository.save(network);
        // the normalizer reads the catalog once at start-up, the charges are cached for an hour
        cpoNameNormalizer.init();
        cacheManager.getCache(ChargingBenchmarkCache.CACHE).clear();
        me = createAndSaveUser("bench-me-" + System.nanoTime() + "@example.com");
    }

    @Test
    void withoutLogin_401() {
        ResponseEntity<String> response = restTemplate.getForEntity(url(operator, "DC"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownChargingType_400() {
        assertThat(get(url(operator, "UNKNOWN")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(url(operator, "XX")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tooLongOperatorName_400() {
        assertThat(get(url("x".repeat(101), "DC")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void fewerThanFiveOtherDrivers_204() {
        for (String price : new String[]{"0.39", "0.43", "0.49", "0.59"}) otherDriverPays(price);

        assertThat(get(url(operator, "DC")).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void fiveOtherDrivers_200WithAggregatesOnly() {
        for (String price : new String[]{"0.39", "0.43", "0.49", "0.59", "0.69"}) otherDriverPays(price);

        ResponseEntity<Map> response = restTemplate.exchange(url(operator, "DC"), HttpMethod.GET,
                createAuthRequest(me.getId(), me.getEmail()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("cpoName", operator)
                .containsEntry("chargingType", "DC")
                .containsEntry("drivers", 5)
                .containsKeys("medianPricePerKwh", "lowerQuartilePricePerKwh", "upperQuartilePricePerKwh", "cards")
                .doesNotContainKeys("userIds", "prices");
        assertThat(new BigDecimal(response.getBody().get("medianPricePerKwh").toString())).isEqualByComparingTo("0.49");
    }

    private void otherDriverPays(String pricePerKwh) {
        User other = createAndSaveUser("bench-other-" + System.nanoTime() + "@example.com");
        Car car = createAndSaveCar(other.getId(), CarBrand.CarModel.MODEL_3);
        EvLog base = TestDataBuilder.createTestEvLogWithTimestamp(car.getId(), new BigDecimal("40"),
                new BigDecimal(pricePerKwh).multiply(new BigDecimal("40")), LocalDateTime.now().minusDays(2));
        evLogRepository.save(base.toBuilder()
                .chargingType(ChargingType.DC)
                .publicCharging(true)
                .cpoName(operator)
                .includeInStatistics(true)
                .build());
    }

    private ResponseEntity<String> get(String url) {
        return restTemplate.exchange(url, HttpMethod.GET, createAuthRequest(me.getId(), me.getEmail()), String.class);
    }

    private static String url(String cpo, String type) {
        return "/api/charging-benchmarks?cpo=" + cpo + "&type=" + type;
    }
}
