package com.evmonitor.infrastructure.persistence;

import com.evmonitor.application.EvLogService;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EnergyMeasurementType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.testutil.AbstractPostgresIT;
import com.evmonitor.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teslas Abrechnung liefert neben dem Preis die an der Saeule gemessene Energie. Telemetrie-Logs
 * kennen nur die Netto-Energie am Fahrzeug - die Anreicherung muss Brutto nachtragen, auch bei
 * Logs, die schon bepreist sind.
 */
class TeslaBillingEnrichmentPostgresIT extends AbstractPostgresIT {

    @Autowired EvLogService evLogService;
    @Autowired UserRepository userRepository;
    @Autowired CarRepository carRepository;

    private User user;
    private Car car;

    @BeforeEach
    void setUp() {
        user = userRepository.save(TestDataBuilder.createTestUser("suc-" + UUID.randomUUID().toString().substring(0, 8) + "@t.de"));
        car = carRepository.save(TestDataBuilder.createTestCar(user.getId(), CarBrand.CarModel.MODEL_3, new BigDecimal("75.0")));
    }

    @Test
    void enrich_unpricedLiveLog_writesCostAndGrossKwh() {
        EvLog log = saveLiveLog(null, null);

        boolean written = evLogService.enrichWithTeslaPricing(
                log.getId(), user.getId(), new BigDecimal("20.00"), "Tesla Supercharger Achim", new BigDecimal("44.10"));

        EvLog after = evLogRepository.findById(log.getId()).orElseThrow();
        assertThat(written).isTrue();
        assertThat(after.getCostEur()).isEqualByComparingTo("20.00");
        assertThat(after.getKwhCharged()).isEqualByComparingTo("44.10");
        assertThat(after.getKwhAtVehicle()).isEqualByComparingTo("41.64");
        assertThat(after.getMeasurementType()).isEqualTo(EnergyMeasurementType.AT_CHARGER);
    }

    @Test
    void pricedLiveLogWithoutGross_isCandidate_andKeepsCostAndCpo() {
        EvLog log = saveLiveLog(new BigDecimal("20.00"), "Tesla Supercharger Hohenwarsleben, Germany");

        assertThat(evLogService.findEnrichableTeslaLogs(user.getId(), 30))
                .extracting(EvLogService.EnrichableChargingLog::id).contains(log.getId());

        boolean written = evLogService.enrichWithTeslaPricing(
                log.getId(), user.getId(), new BigDecimal("99.00"), "Tesla Supercharger Anders", new BigDecimal("44.10"));

        EvLog after = evLogRepository.findById(log.getId()).orElseThrow();
        assertThat(written).isTrue();
        assertThat(after.getCostEur()).isEqualByComparingTo("20.00");
        assertThat(after.getCpoName()).isEqualTo("Tesla Supercharger Hohenwarsleben, Germany");
        assertThat(after.getKwhCharged()).isEqualByComparingTo("44.10");
    }

    @Test
    void pricedLogWithGross_isNoCandidate_andIsNotOverwritten() {
        EvLog log = saveLiveLog(new BigDecimal("20.00"), "Tesla Supercharger Achim");
        evLogService.enrichWithTeslaPricing(log.getId(), user.getId(), null, null, new BigDecimal("44.10"));

        assertThat(evLogService.findEnrichableTeslaLogs(user.getId(), 30))
                .extracting(EvLogService.EnrichableChargingLog::id).doesNotContain(log.getId());
        boolean written = evLogService.enrichWithTeslaPricing(
                log.getId(), user.getId(), new BigDecimal("1.00"), null, new BigDecimal("1.00"));

        assertThat(written).isFalse();
        assertThat(evLogRepository.findById(log.getId()).orElseThrow().getKwhCharged()).isEqualByComparingTo("44.10");
    }

    @Test
    void pricedThirdPartyDcLog_isNoCandidate() {
        EvLog log = saveLiveLog(new BigDecimal("30.00"), "IONITY");

        assertThat(evLogService.findEnrichableTeslaLogs(user.getId(), 30))
                .extracting(EvLogService.EnrichableChargingLog::id).doesNotContain(log.getId());
    }

    @Test
    void nonPositiveGrossKwh_isIgnored() {
        EvLog log = saveLiveLog(null, null);

        evLogService.enrichWithTeslaPricing(log.getId(), user.getId(), new BigDecimal("5.00"), null, BigDecimal.ZERO);

        EvLog after = evLogRepository.findById(log.getId()).orElseThrow();
        assertThat(after.getCostEur()).isEqualByComparingTo("5.00");
        assertThat(after.getKwhCharged()).isNull();
        assertThat(after.getMeasurementType()).isEqualTo(EnergyMeasurementType.AT_VEHICLE);
    }

    @Test
    void implausibleGross_isNotWritten() {
        // Sammelabrechnungen oder die Session eines zweiten Teslas: Brutto unter Netto oder weit
        // darueber kann nicht zu diesem Log gehoeren.
        EvLog aggregated = saveLiveLog(new BigDecimal("20.00"), "Tesla Supercharger Achim");
        EvLog belowNet = saveLiveLog(new BigDecimal("20.00"), "Tesla Supercharger Achim", 3);

        evLogService.enrichWithTeslaPricing(aggregated.getId(), user.getId(), null, null, new BigDecimal("120.0"));
        evLogService.enrichWithTeslaPricing(belowNet.getId(), user.getId(), null, null, new BigDecimal("30.0"));

        assertThat(evLogRepository.findById(aggregated.getId()).orElseThrow().getKwhCharged()).isNull();
        assertThat(evLogRepository.findById(belowNet.getId()).orElseThrow().getKwhCharged()).isNull();
    }

    private EvLog saveLiveLog(BigDecimal costEur, String cpoName) {
        return saveLiveLog(costEur, cpoName, 0);
    }

    private EvLog saveLiveLog(BigDecimal costEur, String cpoName, int hoursEarlier) {
        LocalDateTime now = LocalDateTime.now().minusDays(2).minusHours(hoursEarlier).withNano(0);
        return evLogRepository.save(EvLog.builder()
                .id(UUID.randomUUID())
                .carId(car.getId())
                .kwhAtVehicle(new BigDecimal("41.64"))
                .costEur(costEur)
                .chargeDurationMinutes(24)
                .loggedAt(now)
                .dataSource(DataSource.TESLA_LIVE)
                .includeInStatistics(true)
                .chargingType(ChargingType.DC)
                .publicCharging(true)
                .cpoName(cpoName)
                .measurementType(EnergyMeasurementType.AT_VEHICLE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
