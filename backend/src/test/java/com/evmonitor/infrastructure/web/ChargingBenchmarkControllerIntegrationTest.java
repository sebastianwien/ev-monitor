package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingBenchmarkCache;
import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.infrastructure.persistence.JpaChargingProviderTariffRepository;
import com.evmonitor.infrastructure.persistence.ChargingNetworkEntity;
import com.evmonitor.infrastructure.persistence.EmpEntity;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    @Autowired JpaEmpRepository empRepository;
    @Autowired ChargingProviderTariffRepository tariffRepository;
    @Autowired JpaChargingProviderTariffRepository jpaTariffRepository;

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
        // /tariffs liest alle gültigen Tarife; Tarife anderer Testklassen ohne Betreiberbindung gälten sonst auch hier
        jpaTariffRepository.deleteAll();
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

    @Test
    void tariffs_withoutLogin_401() {
        ResponseEntity<String> response = restTemplate.getForEntity(tariffsUrl(operator, "DC"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tariffs_invalidInput_400() {
        assertThat(get(tariffsUrl(operator, "UNKNOWN")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(tariffsUrl("x".repeat(101), "DC")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tariffs_withoutCatalogTariff_204_evenWithManyDrivers() {
        for (String price : new String[]{"0.22", "0.22", "0.22", "0.22", "0.22"}) otherDriverPays(price);

        assertThat(get(tariffsUrl(operator, "DC")).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void tariffs_ownNetworkTariff_200AsDirect_withoutDrivers() {
        EmpEntity emp = new EmpEntity();
        emp.setName(operator);
        empRepository.save(emp);
        tariffRepository.save(ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(operator)
                .chargingType(ChargingType.DC).pricePerKwh(new BigDecimal("0.44"))
                .sessionFeeEur(BigDecimal.ZERO).monthlyFeeEur(BigDecimal.ZERO)
                .validFrom(LocalDate.now().minusDays(30)).lastVerifiedAt(LocalDateTime.now()).build());

        ResponseEntity<Map> response = restTemplate.exchange(tariffsUrl(operator, "DC"), HttpMethod.GET,
                createAuthRequest(me.getId(), me.getEmail()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("cpoName", operator).containsEntry("chargingType", "DC");
        List<Map<String, Object>> list = (List<Map<String, Object>>) response.getBody().get("tariffs");
        assertThat(list).singleElement().satisfies(t -> {
            assertThat(t).containsEntry("name", operator).containsEntry("direct", true);
            assertThat(new BigDecimal(t.get("pricePerKwh").toString())).isEqualByComparingTo("0.44");
        });
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

    private static String tariffsUrl(String cpo, String type) {
        return "/api/charging-benchmarks/tariffs?cpo=" + cpo + "&type=" + type;
    }

    private static String url(String cpo, String type) {
        return "/api/charging-benchmarks?cpo=" + cpo + "&type=" + type;
    }
}
