package com.evmonitor.infrastructure.weather;

import com.evmonitor.application.EvLogSavedEvent;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.domain.weather.TemperatureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Beim Speichern faellt die Herkunftsfrage: bringt das Log eine Temperatur mit, wurde sie
 * gemessen - der Wetterdienst wird gar nicht erst gefragt, und die Herkunft wird festgehalten,
 * damit der Backfill die Zeile spaeter nicht einsammelt und ueberschreibt.
 */
@ExtendWith(MockitoExtension.class)
class TemperatureEnrichmentEventListenerTest {

    private static final UUID LOG_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 14, 12, 5);

    @Mock TemperatureEnricher temperatureEnricher;
    @Mock EvLogRepository evLogRepository;
    @InjectMocks TemperatureEnrichmentEventListener listener;

    @Test
    void haeltEineMitgelieferteTemperaturAlsMessungFest() {
        listener.onEvLogSaved(new EvLogSavedEvent(LOG_ID, "u33df5x", AT, 27.5));

        verify(evLogRepository).updateTemperatureSource(LOG_ID, TemperatureSource.MEASURED);
        verifyNoInteractions(temperatureEnricher);
    }

    @Test
    void fragtDenWetterdienstNurOhneEigenenWert() {
        listener.onEvLogSaved(new EvLogSavedEvent(LOG_ID, "u33df5x", AT, null));

        verify(temperatureEnricher).enrichLog(LOG_ID, "u33df5x", AT);
        verifyNoInteractions(evLogRepository);
    }

    @Test
    void machtOhneOrtGarNichts() {
        listener.onEvLogSaved(new EvLogSavedEvent(LOG_ID, null, AT, null));

        verifyNoInteractions(temperatureEnricher, evLogRepository);
    }
}
