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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

    /**
     * Regression: der Handler laeuft AFTER_COMMIT, also ohne aktive Transaktion. Eine
     * DML-Query (updateTemperatureSource) braucht daher eine EIGENE Transaktion -
     * REQUIRED wuerde die bereits committete/sterbende Transaktion joinen und mit
     * "Executing an update/delete query" scheitern.
     */
    @Test
    void handlerLaeuftInEigenerTransaktion() throws Exception {
        Method m = TemperatureEnrichmentEventListener.class.getMethod("onEvLogSaved", EvLogSavedEvent.class);

        TransactionalEventListener tel = m.getAnnotation(TransactionalEventListener.class);
        assertNotNull(tel);
        assertEquals(TransactionPhase.AFTER_COMMIT, tel.phase());

        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx, "AFTER_COMMIT-DML braucht eine eigene Transaktion");
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation());
    }
}
