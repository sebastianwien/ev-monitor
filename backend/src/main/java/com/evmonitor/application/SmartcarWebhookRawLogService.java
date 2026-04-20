package com.evmonitor.application;

import com.evmonitor.domain.SmartcarWebhookRawLog;
import com.evmonitor.domain.SmartcarWebhookRawLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartcarWebhookRawLogService {

    private final SmartcarWebhookRawLogRepository repo;

    @Transactional
    public void log(InternalSmartcarWebhookLogRequest request) {
        SmartcarWebhookRawLog entry = SmartcarWebhookRawLog.builder()
                .eventId(request.eventId())
                .smartcarVehicleId(request.smartcarVehicleId())
                .make(request.make())
                .model(request.model())
                .year(request.year())
                .triggers(request.triggersJson())
                .signals(request.signalsJson())
                .socPercent(request.socPercent())
                .odometerKm(request.odometerKm())
                .locationGeohash(request.locationGeohash())
                .outsideTempCelsius(request.outsideTempCelsius())
                .mode(request.mode())
                .build();
        try {
            repo.save(entry);
        } catch (DataIntegrityViolationException e) {
            // Smartcar may deliver the same webhook more than once - silently ignore duplicates
            log.debug("[SMARTCAR-RAW-LOG] Duplicate eventId={} ignored", request.eventId());
        }
    }
}
