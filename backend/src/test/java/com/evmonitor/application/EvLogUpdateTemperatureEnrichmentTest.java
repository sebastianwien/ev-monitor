package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteRepository;
import com.evmonitor.domain.ChargingSiteSource;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** Ort nachgetragen ueber den Weg des Controllers - die Temperatur muss sofort ermittelt werden, nicht erst im naechtlichen Backfill. */
class EvLogUpdateTemperatureEnrichmentTest extends AbstractIntegrationTest {

    @Autowired
    private EvLogService evLogService;

    @Autowired
    private ChargingSiteRepository chargingSiteRepository;

    @MockitoBean
    private TemperatureEnricher temperatureEnricher;

    @Test
    void updateLogAwardingCoins_withNewLocation_triggersEnrichment() {
        var user = createAndSaveUser("update-enrich-" + UUID.randomUUID() + "@test.com");
        var car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = evLogRepository.save(EvLog.createNew(
                car.getId(), new BigDecimal("50.00"), null, 60, null, 61000,
                null, new BigDecimal("90"), LocalDateTime.now().minusDays(1),
                ChargingType.DC, null, null, true, null));

        evLogService.updateLogAwardingCoins(log.getId(), user.getId(),
                new EvLogUpdateRequest(null, null, null, 48.4994, 14.5039, null, null,
                        null, null, null, null, null, null));

        verify(temperatureEnricher).enrichLog(eq(log.getId()), anyString(), any());
    }

    @Test
    void updateLogAwardingCoins_withChargingSite_triggersEnrichmentAtSiteGeohash() {
        var user = createAndSaveUser("enrich-site-" + System.nanoTime() + "@test.com");
        var car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);
        EvLog log = evLogRepository.save(EvLog.createNew(
                car.getId(), new BigDecimal("50.00"), null, 60, null, 61000,
                null, new BigDecimal("90"), LocalDateTime.now().minusDays(1),
                ChargingType.DC, null, null, true, null));
        // Bereits bekannter Standort: resolve() findet ihn ohne Registerabruf
        String cell = "u2d7fjq";
        chargingSiteRepository.save(new ChargingSite(UUID.randomUUID(), "Billa Freistadt", null, cell,
                null, new BigDecimal("50"), 2, ChargingSiteSource.REGISTER,
                new ChargingSite.RegisterDetails(null, null, null, null, null, List.of(), null, null, null, null),
                LocalDateTime.now()));

        evLogService.updateLogAwardingCoins(log.getId(), user.getId(),
                new EvLogUpdateRequest(null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, true, null, null, null, null, new ChargingSiteRef("Billa Freistadt", cell)));

        verify(temperatureEnricher).enrichLog(eq(log.getId()), eq(cell), any());
    }
}
