package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.persistence.ChargingBenchmarkQueryRepository.PublicChargePrice;
import com.evmonitor.testutil.AbstractPostgresIT;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Raw material of the operator benchmark: priced public sessions with a type and an operator,
 * counted for statistics, from the last twelve months. The price is the cost per kWh at the
 * column, without it per kWh at the vehicle - as the app shows it per session. A card name only
 * when the card is neither private nor deleted. All Postgres tests share one database, so the
 * result is narrowed to this test's driver.
 */
class ChargingBenchmarkQueryPostgresIT extends AbstractPostgresIT {

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Autowired ChargingBenchmarkQueryRepository queryRepository;
    @Autowired JpaUserChargingProviderRepository providerRepository;
    @Autowired JpaChargingProviderTariffRepository jpaTariffRepository;
    @Autowired ChargingProviderTariffRepository tariffRepository;
    @Autowired CarRepository carRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;

    private int hour;

    @Test
    void returnsPricedPublicSessionsWithTypeOperatorAndCard() {
        User user = userRepository.save(TestDataBuilder.createTestUser("bench-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com"));
        Car car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, BigDecimal.valueOf(60)));
        UUID publicCard = card(user.getId(), "EnBW mobility+", false, false);
        UUID privateCard = card(user.getId(), "Zuhause", true, false);
        UUID deletedCard = card(user.getId(), "Alte Karte", false, true);

        save(log(car, "40", null, "23.60").chargingProviderId(publicCard));
        save(log(car, null, "40", "20.00"));
        save(log(car, "40", null, "16.00").chargingProviderId(privateCard));
        save(log(car, "40", null, "18.00").chargingProviderId(deletedCard));
        // not counted: home, older than a year, out of statistics, free, no type, no operator
        save(log(car, "40", null, "9.00").publicCharging(false));
        save(log(car, "40", null, "9.00").loggedAt(NOW.minusMonths(13)));
        save(log(car, "40", null, "9.00").includeInStatistics(false));
        save(log(car, "40", null, "0").costEur(BigDecimal.ZERO));
        // the domain derives a type on save, older rows still carry UNKNOWN
        EvLog unknownType = evLogRepository.save(log(car, "40", null, "9.00").build());
        jdbc.update("UPDATE ev_log SET charging_type = 'UNKNOWN' WHERE id = ?", unknownType.getId());
        save(log(car, "40", null, "9.00").cpoName(null));
        EvLog deleted = evLogRepository.save(log(car, "40", null, "9.00").build());
        evLogRepository.softDelete(deleted.getId());

        List<PublicChargePrice> rows = queryRepository.publicChargePrices(NOW.minusMonths(12)).stream()
                .filter(r -> r.userId().equals(user.getId()))
                .toList();

        assertThat(rows).extracting(PublicChargePrice::cpoName, PublicChargePrice::chargingType, PublicChargePrice::cardName)
                .containsOnly(tuple("EnBW", ChargingType.DC, "EnBW mobility+"), tuple("EnBW", ChargingType.DC, null));
        assertThat(rows).extracting(r -> r.pricePerKwh().setScale(3, java.math.RoundingMode.HALF_UP))
                .containsExactlyInAnyOrder(new BigDecimal("0.590"), new BigDecimal("0.500"),
                        new BigDecimal("0.400"), new BigDecimal("0.450"));
    }

    @Test
    void validTariffs_startedAndNotEnded() {
        LocalDate today = LocalDate.now();
        UUID valid = tariff(today.minusDays(10), null);
        UUID endsToday = tariff(today.minusDays(10), today);
        UUID ended = tariff(today.minusDays(10), today.minusDays(1));
        UUID future = tariff(today.plusDays(1), null);

        List<UUID> ids = tariffRepository.findValidOn(today).stream().map(ChargingProviderTariff::getId).toList();

        assertThat(ids).contains(valid, endsToday).doesNotContain(ended, future);
    }

    private EvLog.EvLogBuilder log(Car car, String kwhAtColumn, String kwhAtVehicle, String cost) {
        EvLog base = TestDataBuilder.createTestEvLogWithTimestamp(car.getId(),
                kwhAtColumn == null ? null : new BigDecimal(kwhAtColumn), new BigDecimal(cost), NOW.minusDays(1).minusHours(hour++));
        return base.toBuilder()
                .kwhAtVehicle(kwhAtVehicle == null ? null : new BigDecimal(kwhAtVehicle))
                .chargingType(ChargingType.DC)
                .publicCharging(true)
                .cpoName("EnBW")
                .includeInStatistics(true);
    }

    private void save(EvLog.EvLogBuilder log) {
        evLogRepository.save(log.build());
    }

    private UUID card(UUID owner, String name, boolean privateCard, boolean deleted) {
        UserChargingProviderEntity p = new UserChargingProviderEntity();
        p.setUserId(owner);
        p.setProviderName(name);
        p.setDcPricePerKwh(new BigDecimal("0.39"));
        p.setActiveFrom(LocalDate.now().minusYears(1));
        p.setPrivateCard(privateCard);
        if (deleted) p.setDeletedAt(LocalDateTime.now());
        return providerRepository.save(p).getId();
    }

    private UUID tariff(LocalDate from, LocalDate until) {
        ChargingProviderTariffEntity e = new ChargingProviderTariffEntity();
        e.setId(UUID.randomUUID());
        e.setEmpName("EnBW");
        e.setTariffVariant("Test " + UUID.randomUUID().toString().substring(0, 8));
        e.setChargingType("DC");
        e.setPricePerKwh(new BigDecimal("0.3900"));
        e.setSessionFeeEur(BigDecimal.ZERO);
        e.setMonthlyFeeEur(BigDecimal.ZERO);
        e.setValidFrom(from);
        e.setValidUntil(until);
        e.setLastVerifiedAt(LocalDateTime.now());
        return jpaTariffRepository.save(e).getId();
    }
}
