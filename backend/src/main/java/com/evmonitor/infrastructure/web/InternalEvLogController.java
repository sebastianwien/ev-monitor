package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.EvLogService;
import com.evmonitor.application.InternalEvLogRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
