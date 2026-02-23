package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.EvLogStatisticsResponse;
import com.evmonitor.application.EvLogService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "*") // Keep open for now, specify later depending on Nginx setup
public class EvLogController {

    private final EvLogService evLogService;

    public EvLogController(EvLogService evLogService) {
        this.evLogService = evLogService;
    }

    @PostMapping
    public ResponseEntity<EvLogResponse> logDrive(@RequestBody EvLogRequest request, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        EvLogResponse response = evLogService.logDrive(principal.getUser().getId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<EvLogResponse>> getAllLogs(
            @RequestParam(required = false) UUID carId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        List<EvLogResponse> logs;

        if (carId != null) {
            logs = evLogService.getLogsForCar(carId, principal.getUser().getId());
        } else {
            logs = evLogService.getAllLogsForUser(principal.getUser().getId());
        }

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvLogResponse> getLogById(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        EvLogResponse log = evLogService.getLogByIdForUser(id, principal.getUser().getId());
        return ResponseEntity.ok(log);
    }

    @GetMapping("/statistics")
    public ResponseEntity<EvLogStatisticsResponse> getStatistics(
            @RequestParam UUID carId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        EvLogStatisticsResponse stats = evLogService.getStatistics(carId, principal.getUser().getId());
        return ResponseEntity.ok(stats);
    }
}
