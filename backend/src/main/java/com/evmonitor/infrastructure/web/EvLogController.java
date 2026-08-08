package com.evmonitor.infrastructure.web;

import ch.hsr.geohash.GeoHash;
import com.evmonitor.application.EvLogCreateResponse;
import com.evmonitor.application.EvLogRequest;
import com.evmonitor.application.EvLogResponse;
import com.evmonitor.application.EvLogStatisticsResponse;
import com.evmonitor.application.EvLogStatisticsService;
import com.evmonitor.application.EvLogUpdateRequest;
import com.evmonitor.application.EvLogService;
import com.evmonitor.application.GeohashResponse;
import com.evmonitor.application.PeerModelComparisonResponse;
import com.evmonitor.domain.CarRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.Car;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class EvLogController {

    private final EvLogService evLogService;
    private final EvLogStatisticsService evLogStatisticsService;
    private final CarRepository carRepository;


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
        return evLogStatisticsService.getPriceSuggestion(principal.getUser().getId(), lat, lon, isPublic)
                .map(suggestion -> {
                    Map<String, Object> body = new java.util.HashMap<>();
                    body.put("costPerKwh", suggestion.costPerKwh());
                    if (suggestion.chargingProviderId() != null) {
                        body.put("chargingProviderId", suggestion.chargingProviderId());
                    }
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvLogResponse> getLogById(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        EvLogResponse log = evLogService.getLogByIdForUser(id, principal.getUser().getId());
        return ResponseEntity.ok(log);
    }

    /**
     * Returns the persisted charging power curve for the given log, or 404 if the
     * log doesn't exist or doesn't belong to the caller. 200 with empty points
     * when the log exists but has no curve (most data sources).
     *
     * <p>Caching: Power-curves sind nach Session-Finalize immutable, daher
     * private max-age=7d + ETag basierend auf der Log-ID. Spart Re-Fetches beim
     * Auf-/Zuklappen derselben Curve im Logfeed.
     */
    @GetMapping("/{id}/power-curve")
    public ResponseEntity<com.evmonitor.application.PowerCurveResponse> getPowerCurve(
            @PathVariable UUID id, Authentication authentication,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            String etag = "\"pc-" + id + "\"";
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_MODIFIED).eTag(etag).build();
            }
            com.evmonitor.application.PowerCurveResponse body =
                    evLogService.getPowerCurveForUser(id, principal.getUser());
            return ResponseEntity.ok()
                    .eTag(etag)
                    .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate())
                    .body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
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
     * How many logs at this location still lack a price - drives the "apply to all N" prompt.
     *
     * Accepts either an existing {@code geohash} (editing a stored log - lat/lon are never
     * persisted, so that is all the client has) or {@code lat}/{@code lon} (creating a new log,
     * same contract as /price-suggestion). lat/lon are only used to derive the geohash.
     */
    @GetMapping("/priceless-count")
    public ResponseEntity<Map<String, Long>> countPricelessLogsAtLocation(
            @RequestParam(required = false) String geohash,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "false") boolean isPublic,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String location = resolveGeohash(geohash, lat, lon, isPublic);
        if (location == null) return ResponseEntity.badRequest().build();

        long count = evLogService.countPricelessLogsAtLocation(principal.getUser().getId(), location);
        return ResponseEntity.ok(Map.of("count", count));
    }

    record ApplyTariffRequest(String geohash, Double lat, Double lon, boolean isPublic, UUID chargingProviderId) {}

    /** Prices all cost-less logs at this location with the given charging card. Never overwrites existing costs. */
    @PatchMapping("/apply-tariff-at-location")
    public ResponseEntity<Map<String, Integer>> applyTariffAtLocation(
            @RequestBody ApplyTariffRequest body,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (body.chargingProviderId() == null) {
            return ResponseEntity.badRequest().build();
        }
        String location = resolveGeohash(body.geohash(), body.lat(), body.lon(), body.isPublic());
        if (location == null) return ResponseEntity.badRequest().build();

        try {
            int priced = evLogService.applyTariffAtLocation(
                    principal.getUser().getId(), location, body.chargingProviderId());
            return ResponseEntity.ok(Map.of("priced", priced));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * A stored geohash wins over lat/lon. Returns null when neither is usable, so the caller
     * answers 400 instead of silently counting the wrong location.
     *
     * Precision mirrors the log-creation path: public chargers 7 chars (~150m), private 6 (~600m).
     * A client-supplied geohash is never a leak - the query is always scoped to the caller's own logs.
     */
    private static String resolveGeohash(String geohash, Double lat, Double lon, boolean isPublic) {
        if (geohash != null && !geohash.isBlank()) return geohash;
        if (lat == null || lon == null) return null;
        return GeoHash.withCharacterPrecision(lat, lon, isPublic ? 7 : 6).toBase32();
    }

    @GetMapping("/geohashes")
    public ResponseEntity<List<GeohashResponse>> getGeohashData(
            @RequestParam UUID carId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            List<GeohashResponse> data = evLogStatisticsService.getGeohashData(carId, principal.getUser().getId());
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
            List<EvLogResponse> logs = evLogStatisticsService.getImplausibleLogs(carId, principal.getUser().getId());
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

    record MergeLogRequest(UUID sourceLogId, boolean preferSource) {}

    /**
     * Fehler (nicht gefunden / fremder Log / Konflikt) werden vom GlobalExceptionHandler
     * auf 404/403/409 gemappt - hier bewusst kein try/catch, das alles auf 404 planiert.
     */
    @PatchMapping("/{logId}/merge")
    public ResponseEntity<Void> mergeLog(
            @PathVariable UUID logId,
            @RequestBody MergeLogRequest body,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (body.sourceLogId() == null) {
            return ResponseEntity.badRequest().build();
        }
        evLogService.mergeLog(logId, body.sourceLogId(), principal.getUser().getId(), body.preferSource());
        return ResponseEntity.ok().build();
    }

    /**
     * Weist einen einzelnen Log einem anderen Fahrzeug desselben Users zu.
     */
    @PatchMapping("/{logId}/car")
    public ResponseEntity<Void> reassignLogCar(
            @PathVariable UUID logId,
            @RequestBody Map<String, UUID> body,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID targetCarId = body.get("targetCarId");
        if (targetCarId == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            evLogService.reassignLog(logId, targetCarId, principal.getUser().getId());
            return ResponseEntity.ok().build();
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

        EvLogStatisticsResponse stats = evLogStatisticsService.getStatistics(
                carId,
                principal.getUser().getId(),
                computedStartDate,
                computedEndDate,
                groupBy
        );
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/logs/{carId}/peer-model-comparison
     * Returns peer comparison grouped by vehicle specification for the same car model.
     * User must own the car.
     */
    @GetMapping("/{carId}/peer-model-comparison")
    public ResponseEntity<PeerModelComparisonResponse> getPeerModelComparison(
            @PathVariable UUID carId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        // Fetch car and validate ownership
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found"));
        if (!car.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Car does not belong to user");
        }

        PeerModelComparisonResponse response = evLogStatisticsService.getPeerModelComparison(car, user);
        return ResponseEntity.ok(response);
    }
}
