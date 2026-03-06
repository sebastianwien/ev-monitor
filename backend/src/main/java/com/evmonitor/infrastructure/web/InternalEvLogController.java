package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.EvLogService;
import com.evmonitor.application.InternalEvLogRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal endpoints for log creation by the Wallbox Service.
 * Secured by InternalAuthFilter (X-Internal-Token header), NOT by user JWT.
 */
@RestController
@RequestMapping("/api/internal")
public class InternalEvLogController {

    private final EvLogService evLogService;

    public InternalEvLogController(EvLogService evLogService) {
        this.evLogService = evLogService;
    }

    @PostMapping("/logs")
    public ResponseEntity<EvLogResponse> createWallboxLog(@Valid @RequestBody InternalEvLogRequest request) {
        EvLogResponse response = evLogService.createWallboxLog(request);
        return ResponseEntity.ok(response);
    }

    public record GeohashUpdateRequest(UUID carId, UUID userId, LocalDateTime loggedAt, String geohash) {}

    @PatchMapping("/logs/geohash")
    public ResponseEntity<Void> updateGeohash(@RequestBody GeohashUpdateRequest request) {
        evLogService.updateGeohash(request.carId(), request.userId(), request.loggedAt(), request.geohash());
        return ResponseEntity.noContent().build();
    }
}
