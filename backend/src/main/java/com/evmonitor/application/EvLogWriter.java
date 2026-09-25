package com.evmonitor.application;

import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Speichert ein Log und stößt die Folgearbeit an (Wetter, SoH-Erkennung). Eigene Klasse, weil
 * {@link EvLogService} und das {@code IngestGateway} beide speichern und das Gateway sonst über
 * {@link EvLogService#createInternalLog} einen Zyklus mit ihm bilden würde.
 *
 * <p>Bewusst ohne {@code @Transactional}: läuft in der Transaktion des Aufrufers. Die Events
 * sind AFTER_COMMIT; ohne Transaktion verfallen sie wie bisher bei Self-Invocation von
 * {@link EvLogService#save}. Ein eigener Proxy würde das still ändern.
 */
@Component
@RequiredArgsConstructor
public class EvLogWriter {

    private final EvLogRepository evLogRepository;
    private final CarRepository carRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EvLog save(EvLog evLog) {
        EvLog saved = evLogRepository.save(evLog);
        eventPublisher.publishEvent(EvLogSavedEvent.of(saved.getId(), saved.getGeohash(), saved.getLoggedAt(),
                saved.getChargeDurationMinutes(), saved.getTemperatureCelsius()));
        if (saved.getKwhAtVehicle() != null) {
            carRepository.findById(saved.getCarId()).ifPresent(car ->
                    eventPublisher.publishEvent(new SohAutoDetectEvent(car)));
        }
        return saved;
    }
}
