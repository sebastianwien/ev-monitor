package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.EvLogService;
import com.evmonitor.application.InternalEvLogRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Internal endpoints for log creation by the Wallbox Service.
 * Secured by InternalAuthFilter (X-Internal-Token header), NOT by user JWT.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalEvLogController {

    private final EvLogService evLogService;

    @PostMapping("/logs")
    public ResponseEntity<EvLogResponse> createInternalLog(@RequestBody InternalEvLogRequest request) {
        try {
            EvLogResponse response = evLogService.createInternalLog(request);
            if (response == null) {
                return ResponseEntity.ok().build(); // already imported — idempotent
            }
            return ResponseEntity.ok(response);
        } catch (DataIntegrityViolationException e) {
            // Race condition: two webhooks with identical timestamp both passed the duplicate
            // check simultaneously — treat as idempotent success instead of 500.
            return ResponseEntity.ok().build();
        }
    }

    public record GeohashUpdateRequest(UUID carId, UUID userId, LocalDateTime loggedAt, String geohash) {}

    @PatchMapping("/logs/geohash")
    public ResponseEntity<Void> updateGeohash(@RequestBody GeohashUpdateRequest request) {
        evLogService.updateGeohash(request.carId(), request.userId(), request.loggedAt(), request.geohash());
        return ResponseEntity.noContent().build();
    }

    public record TemperatureUpdateRequest(UUID carId, UUID userId, LocalDateTime loggedAt, Double temperatureCelsius) {}

    @PatchMapping("/logs/temperature")
    public ResponseEntity<Void> updateTemperature(@RequestBody TemperatureUpdateRequest request) {
        evLogService.backfillTemperature(request.carId(), request.userId(), request.loggedAt(), request.temperatureCelsius());
        return ResponseEntity.noContent().build();
    }

    /**
     * Listet Tesla-Ladungen des Users ohne Kosten, die aus Teslas Billing-API stammen koennten.
     * Der Enrichment-Job im connectors-service gleicht sie gegen /dx/charging/history ab und
     * bepreist die Treffer - welche Ladung ein Supercharger war, weiss nur Tesla.
     */
    @GetMapping("/users/{userId}/enrichable-tesla-logs")
    public ResponseEntity<List<EvLogService.EnrichableChargingLog>> enrichableTeslaLogs(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(evLogService.findEnrichableTeslaLogs(userId, days));
    }

    public record EnrichTeslaRequest(UUID userId, BigDecimal costEur, String cpoName) {}

    /**
     * Traegt Abrechnungsdaten aus /dx/charging/history in ein Tesla-Log nach. Kostenlose Ladungen
     * ({@code costEur=0}) fallen damit aus dem Kandidatenkreis des naechsten Laufs.
     * Defense-in-Depth: Service und Repository lassen nur Logs ohne Kosten aus einer Tesla-Quelle
     * zu - eine falsche id ist ein stiller No-Op, kein Ueberschreiben fremder Daten.
     */
    @PatchMapping("/logs/{id}/enrich-tesla")
    public ResponseEntity<Void> enrichTesla(@PathVariable UUID id, @RequestBody EnrichTeslaRequest request) {
        evLogService.enrichWithTeslaPricing(id, request.userId(), request.costEur(), request.cpoName());
        return ResponseEntity.noContent().build();
    }
}
