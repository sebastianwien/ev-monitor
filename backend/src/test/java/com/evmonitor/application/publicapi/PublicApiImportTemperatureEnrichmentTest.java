package com.evmonitor.application.publicapi;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicApiImportTemperatureEnrichmentTest extends AbstractIntegrationTest {

    @Autowired
    private PublicApiImportService importService;

    @MockitoBean
    private TemperatureEnricher temperatureEnricher;

    @Test
    void importWithLocation_triggersEnrichment() {
        var user = createAndSaveUser("import-enrich-" + UUID.randomUUID() + "@test.com");
        var car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        importService.importSessions(user.getId(),
                new PublicApiSessionRequest(car.getId(), List.of(entry("2024-06-01 10:00", 20.0, "48.1371 11.5754", null))),
                DataSource.API_UPLOAD);

        verify(temperatureEnricher).enrichLog(any(UUID.class), anyString(), any());
    }

    @Test
    void importWithoutLocation_skipsEnrichment() {
        var user = createAndSaveUser("import-noloc-" + UUID.randomUUID() + "@test.com");
        var car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        importService.importSessions(user.getId(),
                new PublicApiSessionRequest(car.getId(), List.of(entry("2024-06-01 10:00", 20.0, null, null))),
                DataSource.API_UPLOAD);

        verifyNoInteractions(temperatureEnricher);
    }

    @Test
    void importWithTemperatureAlreadyProvided_skipsEnrichment() {
        var user = createAndSaveUser("import-temp-" + UUID.randomUUID() + "@test.com");
        var car = createAndSaveCar(user.getId(), CarBrand.CarModel.MODEL_3);

        importService.importSessions(user.getId(),
                new PublicApiSessionRequest(car.getId(), List.of(entry("2024-06-01 10:00", 20.0, "48.1371 11.5754", 15.0))),
                DataSource.API_UPLOAD);

        verifyNoInteractions(temperatureEnricher);
    }

    private PublicApiSessionRequest.SessionEntry entry(String date, double kwh, String location, Double temp) {
        return new PublicApiSessionRequest.SessionEntry(
                date, kwh, null, null, null, null, null, null,
                location, null, null, null, null, null, false, null, null, temp);
    }
}
