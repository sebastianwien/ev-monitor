package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.CpoNameNormalizer;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.ChargingNetworkEntity;
import com.evmonitor.infrastructure.persistence.EmpEntity;
import com.evmonitor.infrastructure.persistence.JpaChargingNetworkRepository;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * "With this card you would have saved X €" for one own charge: only the owner may ask, only
 * charges in Germany with charger kWh and a known operator count, only maintained tariffs.
 * 204 when nothing qualifies, so the story simply shows no card section.
 */
class CardSavingsIntegrationTest extends AbstractIntegrationTest {

    private static final String BERLIN = "u33dc0b";
    private static final String SALZBURG = "u23rbvz";

    @Autowired ChargingProviderTariffRepository tariffs;
    @Autowired JpaEmpRepository emps;
    @Autowired JpaChargingNetworkRepository networks;
    @Autowired CpoNameNormalizer normalizer;

    private User owner;
    private Car car;
    private String operator;
    private String emp;

    @BeforeEach
    void setUp() {
        owner = createAndSaveUser("savings-" + System.nanoTime() + "@example.com");
        car = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        operator = "Netz-" + suffix;
        emp = "Karte-" + suffix;
        ChargingNetworkEntity n = new ChargingNetworkEntity();
        n.setName(operator);
        networks.save(n);
        EmpEntity e = new EmpEntity();
        e.setName(emp);
        emps.save(e);
        normalizer.init();
        tariffs.save(ChargingProviderTariff.builder().id(UUID.randomUUID()).empName(emp).tariffVariant("Standard")
                .chargingType(ChargingType.DC).pricePerKwh(new BigDecimal("0.62")).sessionFeeEur(BigDecimal.ZERO)
                .monthlyFeeEur(BigDecimal.ZERO).appliesElsewhere(true).validFrom(LocalDate.of(2026, 1, 1))
                .lastVerifiedAt(LocalDateTime.of(2026, 10, 27, 12, 0)).build());
    }

    @Test
    void ownChargeInGermany_listsTheCardWithItsSaving() {
        EvLog log = charge(BERLIN, "40", "35.60");

        ResponseEntity<Map> res = get(log.getId(), owner);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("cpoName", operator).containsEntry("chargingType", "DC");
        List<Map<String, Object>> offers = (List<Map<String, Object>>) res.getBody().get("offers");
        assertThat(offers).anySatisfy(o -> {
            assertThat(o).containsEntry("name", emp).containsEntry("verifiedOn", "2026-10-27");
            assertThat(((Number) o.get("chargeSavingsEur")).doubleValue()).isEqualTo(10.80);
        });
    }

    @Test
    void someoneElsesCharge_isForbidden() {
        EvLog log = charge(BERLIN, "40", "35.60");
        User other = createAndSaveUser("other-" + System.nanoTime() + "@example.com");

        assertThat(get(log.getId(), other).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(UUID.randomUUID(), owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void chargeAbroad_orWithoutLocation_hasNoSavings() {
        assertThat(get(charge(SALZBURG, "40", "35.60").getId(), owner).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get(charge(null, "40", "35.60").getId(), owner).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void cheapCharge_hasNoSavings() {
        assertThat(get(charge(BERLIN, "40", "20.00").getId(), owner).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private EvLog charge(String geohash, String kwh, String cost) {
        return evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID())
                .carId(car.getId())
                .kwhCharged(new BigDecimal(kwh))
                .costEur(new BigDecimal(cost))
                .chargeDurationMinutes(30)
                .geohash(geohash)
                .maxChargingPowerKw(new BigDecimal("150"))
                .loggedAt(LocalDateTime.now().minusHours(3).withNano(0).minusSeconds((long) (Math.random() * 1000)))
                .dataSource(DataSource.USER_LOGGED)
                .includeInStatistics(true)
                .chargingType(ChargingType.DC)
                .publicCharging(true)
                .cpoName(operator)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    private ResponseEntity<Map> get(UUID logId, User user) {
        return restTemplate.exchange("/api/charging-benchmarks/savings?logId=" + logId, HttpMethod.GET,
                createAuthRequest(user.getId(), user.getEmail()), Map.class);
    }
}
