package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogCreateResponse;
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
public class EvLogController {

    private final EvLogService evLogService;

    public EvLogController(EvLogService evLogService) {
        this.evLogService = evLogService;
    }

    @PostMapping
    public ResponseEntity<EvLogCreateResponse> logCharging(@RequestBody EvLogRequest request, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        EvLogCreateResponse response = evLogService.logCharging(principal.getUser().getId(), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<EvLogResponse>> getAllLogs(
            @RequestParam(required = false) UUID carId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // Hard cap: never return more than 50 logs per request
        int effectiveLimit = Math.min(limit != null ? limit : 50, 50);

        List<EvLogResponse> logs;
        if (carId != null) {
            logs = evLogService.getLogsForCar(carId, principal.getUser().getId(), effectiveLimit, page);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLog(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        evLogService.deleteLog(id, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statistics")
    public ResponseEntity<EvLogStatisticsResponse> getStatistics(
            @RequestParam UUID carId,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(defaultValue = "MONTH") String groupBy,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // Handle predefined time ranges
        java.time.LocalDate computedStartDate = startDate;
        java.time.LocalDate computedEndDate = endDate;

        if (timeRange != null && !timeRange.equals("CUSTOM")) {
            java.time.LocalDate today = java.time.LocalDate.now();
            computedEndDate = today;

            computedStartDate = switch (timeRange) {
                case "THIS_MONTH" -> today.withDayOfMonth(1);
                case "LAST_MONTH" -> today.minusMonths(1).withDayOfMonth(1);
                case "LAST_3_MONTHS" -> today.minusMonths(3);
                case "LAST_6_MONTHS" -> today.minusMonths(6);
                case "LAST_12_MONTHS" -> today.minusMonths(12);
                case "THIS_YEAR" -> today.withDayOfYear(1);
                case "ALL_TIME" -> null; // No start date = all time
                default -> null;
            };

            // For LAST_MONTH, end date should be last day of that month
            if ("LAST_MONTH".equals(timeRange)) {
                computedStartDate = today.minusMonths(1).withDayOfMonth(1);
                computedEndDate = today.minusMonths(1).withDayOfMonth(
                        today.minusMonths(1).lengthOfMonth());
            }

            // For ALL_TIME, no date filters
            if ("ALL_TIME".equals(timeRange)) {
                computedStartDate = null;
                computedEndDate = null;
            }
        }

        EvLogStatisticsResponse stats = evLogService.getStatistics(
                carId,
                principal.getUser().getId(),
                computedStartDate,
                computedEndDate,
                groupBy
        );
        return ResponseEntity.ok(stats);
    }
}
