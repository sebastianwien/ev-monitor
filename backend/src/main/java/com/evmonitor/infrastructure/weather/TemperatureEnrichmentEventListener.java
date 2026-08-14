package com.evmonitor.infrastructure.weather;

import com.evmonitor.application.EvLogSavedEvent;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.weather.TemperatureEnricher;
import com.evmonitor.domain.weather.TemperatureSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TemperatureEnrichmentEventListener {

    private final TemperatureEnricher temperatureEnricher;
    private final EvLogRepository evLogRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
