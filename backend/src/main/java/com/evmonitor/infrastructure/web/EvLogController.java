package com.evmonitor.infrastructure.web;

import com.evmonitor.application.EvLogCreateResponse;
import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.EvLogStatisticsResponse;
import com.evmonitor.application.EvLogUpdateRequest;
import com.evmonitor.application.EvLogService;
import com.evmonitor.application.SessionGroupResponse;
import com.evmonitor.application.SessionGroupService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/logs")
public class EvLogController {

    private final EvLogService evLogService;
    private final SessionGroupService sessionGroupService;

    public EvLogController(EvLogService evLogService, SessionGroupService sessionGroupService) {
        this.evLogService = evLogService;
        this.sessionGroupService = sessionGroupService;
    }

    @PostMapping
    public ResponseEntity<?> logCharging(@Valid @RequestBody EvLogRequest request, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            EvLogCreateResponse response = evLogService.logCharging(principal.getUser().getId(), request);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ein Eintrag mit diesem Datum und dieser Uhrzeit existiert bereits für dieses Fahrzeug. Bitte ändere die Uhrzeit."));
        }
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
            logs = evLogService.getStandaloneLogsForUser(principal.getUser().getId());
        }

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/price-suggestion")
    public ResponseEntity<?> getPriceSuggestion(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "false") boolean isPublic,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return evLogService.getPriceSuggestion(principal.getUser().getId(), lat, lon, isPublic)
                .map(price -> ResponseEntity.ok(Map.of("costPerKwh", price)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvLogResponse> getLogById(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        EvLogResponse log = evLogService.getLogByIdForUser(id, principal.getUser().getId());
        return ResponseEntity.ok(log);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateLog(
            @PathVariable UUID id,
            @Valid @RequestBody EvLogUpdateRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            EvLogResponse updated = evLogService.updateLog(id, principal.getUser().getId(), request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ein Eintrag mit diesem Datum und dieser Uhrzeit existiert bereits für dieses Fahrzeug. Bitte ändere die Uhrzeit."));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLog(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        evLogService.deleteLog(id, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/batch")
    public ResponseEntity<Void> deleteLogs(@RequestBody List<UUID> ids, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        for (UUID id : ids) {
            try {
                evLogService.deleteLog(id, principal.getUser().getId());
            } catch (IllegalArgumentException ignored) {
                // Log not found or not owned by user — skip silently
            }
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Gibt alle Gruppen (Überschussladen-Zusammenfassungen) für ein Fahrzeug zurück.
     * Jede Gruppe repräsentiert mehrere Micro-Sessions als einen logischen Ladevorgang.
     */
    @GetMapping("/groups")
    public ResponseEntity<List<SessionGroupResponse>> getGroups(
            @RequestParam UUID carId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            evLogService.verifyCarOwnership(carId, principal.getUser().getId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        List<SessionGroupResponse> groups = sessionGroupService.findAllByCarId(carId);
        return ResponseEntity.ok(groups);
    }

    /**
     * Gibt alle Sub-Sessions einer Gruppe zurück (für die Aufklapp-Ansicht im Dashboard).
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<EvLogResponse>> getGroupSubSessions(
            @PathVariable UUID groupId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        SessionGroupResponse group = sessionGroupService.findById(groupId).orElse(null);
        if (group == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            evLogService.verifyCarOwnership(group.carId(), principal.getUser().getId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build(); // Car not found or ownership mismatch → 404
        }
        List<EvLogResponse> subSessions = sessionGroupService.getSubSessions(groupId);
        return ResponseEntity.ok(subSessions);
    }

    @GetMapping("/geohashes")
    public ResponseEntity<List<com.evmonitor.application.GeohashResponse>> getGeohashData(
            @RequestParam UUID carId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            List<com.evmonitor.application.GeohashResponse> data = evLogService.getGeohashData(carId, principal.getUser().getId());
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/implausible")
    public ResponseEntity<List<EvLogResponse>> getImplausibleLogs(
            @RequestParam UUID carId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            List<EvLogResponse> logs = evLogService.getImplausibleLogs(carId, principal.getUser().getId());
            return ResponseEntity.ok(logs);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/statistics-inclusion")
    public ResponseEntity<?> updateStatisticsInclusion(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Boolean include = body.get("includeInStatistics");
        if (include == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "includeInStatistics is required"));
        }
        try {
            EvLogResponse updated = evLogService.updateIncludeInStatistics(id, principal.getUser().getId(), include);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
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
