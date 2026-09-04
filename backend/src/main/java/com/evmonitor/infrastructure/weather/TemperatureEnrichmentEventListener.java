package com.evmonitor.infrastructure.weather;

import com.evmonitor.application.EvLogSavedEvent;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.domain.weather.TemperatureSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TemperatureEnrichmentEventListener {

    private final TemperatureEnricher temperatureEnricher;
    private final EvLogRepository evLogRepository;

    // AFTER_COMMIT laeuft nach dem Commit der Log-Speicherung - es gibt keine aktive
    // Transaktion mehr. REQUIRES_NEW oeffnet eine eigene Transaktion fuer die DML-Writes;
    // REQUIRED wuerde die bereits committete Transaktion joinen und mit
    // "Executing an update/delete query" scheitern.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEvLogSaved(EvLogSavedEvent event) {
        // Bringt das Log selbst eine Temperatur mit, stammt sie vom Fahrzeug bzw. aus einem
        // Hersteller-Export - das wird festgehalten, damit der Backfill sie nie ueberschreibt.
        if (event.temperatureCelsius() != null) {
            evLogRepository.updateTemperatureSource(event.logId(), TemperatureSource.MEASURED);
            return;
        }
        if (event.geohash() == null) return;
        temperatureEnricher.enrichLog(event.logId(), event.geohash(), event.loggedAt());
    }
}
