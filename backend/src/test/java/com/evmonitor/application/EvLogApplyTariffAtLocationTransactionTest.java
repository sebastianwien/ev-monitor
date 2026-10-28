package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.infrastructure.persistence.JpaUserChargingProviderRepository;
import com.evmonitor.infrastructure.persistence.UserChargingProviderEntity;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Der Sammelnachtrag am Ort läuft in einer Transaktion wie der Heimtarif-Nachtrag: alles oder
 * nichts, und die AFTER_COMMIT-Events des Speicherns (Temperatur, Akkuzustand) feuern danach.
 */
class EvLogApplyTariffAtLocationTransactionTest extends AbstractIntegrationTest {

    private static final String GEOHASH = "u33dc1v";

    @Autowired
    private EvLogService evLogService;

    @Autowired
    private JpaUserChargingProviderRepository chargingProviderRepository;

    @MockitoBean
    private TemperatureEnricher temperatureEnricher;

    @Test
    void locationBatch_firesAfterCommitEventsOfTheSavedLogs() {
        var user = createAndSaveUser("tariff-tx-" + System.nanoTime() + "@example.com");
        var car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = evLogRepository.save(EvLog.createNew(car.getId(), new BigDecimal("50.0"), null,
                45, GEOHASH, 10_000, new BigDecimal("150.0"), new BigDecimal("80.0"),
                LocalDateTime.now().minusDays(2), ChargingType.DC, null, null, true, null));

        UserChargingProviderEntity p = new UserChargingProviderEntity();
        p.setUserId(user.getId());
        p.setProviderName("EnBW mobility+");
        p.setAcPricePerKwh(new BigDecimal("0.3900"));
        p.setDcPricePerKwh(new BigDecimal("0.5900"));
        p.setSessionFeeEur(BigDecimal.ZERO);
        p.setMonthlyFeeEur(BigDecimal.ZERO);
        p.setActiveFrom(LocalDate.now().minusYears(1));
        UUID providerId = chargingProviderRepository.save(p).getId();

        EvLogService.TariffApplied result = evLogService.applyTariffAtLocation(user.getId(), GEOHASH, providerId);

        assertEquals(1, result.priced());
        verify(temperatureEnricher).enrichLog(eq(log.getId()), eq(GEOHASH), any());
    }
}
