package com.evmonitor.infrastructure.weather;

import com.evmonitor.application.EvLogSavedEvent;
import com.evmonitor.domain.weather.TemperatureEnricher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TemperatureEnrichmentEventListener {

    private final TemperatureEnricher temperatureEnricher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvLogSaved(EvLogSavedEvent event) {
        if (event.geohash() == null || event.temperatureCelsius() != null) return;
        temperatureEnricher.enrichLog(event.logId(), event.geohash(), event.loggedAt());
    }
}
