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

    /**
     * Lists Tesla-Supercharger sessions submitted via Telemetry that still lack a Tesla-billed
     * cost, within the given recency window. Consumed by the daily enrichment job in
     * connectors-service to decide which sessions need a /dx/charging/history lookup.
     */
    @GetMapping("/users/{userId}/pending-supercharger-enrichment")
    public ResponseEntity<List<EvLogService.PendingSuperchargerEnrichment>> pendingSuperchargerEnrichment(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(evLogService.findPendingSuperchargerEnrichment(userId, days));
    }

    public record EnrichTeslaRequest(BigDecimal costEur, String cpoName) {}

    /**
     * Enriches an existing Tesla-Supercharger ev_log with billing data fetched from
     * /dx/charging/history. Free-charging sessions ({@code costEur=0}) flip the log out
     * of the pending-enrichment scope on the next sweep.
     * Defense-in-Depth: the service layer + repository ensure only logs that are still
     * in pending-enrichment-state (cpoName='Tesla Supercharger' AND costEur IS NULL) are
     * touched - a wrong id is a silent no-op, not an arbitrary log overwrite.
     */
    @PatchMapping("/logs/{id}/enrich-tesla")
    public ResponseEntity<Void> enrichTesla(@PathVariable UUID id, @RequestBody EnrichTeslaRequest request) {
        evLogService.enrichWithTeslaPricing(id, request.costEur(), request.cpoName());
        return ResponseEntity.noContent().build();
    }
}
