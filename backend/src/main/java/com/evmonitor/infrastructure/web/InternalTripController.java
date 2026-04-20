package com.evmonitor.infrastructure.web;

import com.evmonitor.application.InternalTripRequest;
import com.evmonitor.application.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoint for persisting completed driving trips from connectors-service.
 * Secured by InternalAuthFilter (X-Internal-Token), NOT by user JWT.
 */
@RestController
@RequestMapping("/api/internal/trips")
@RequiredArgsConstructor
public class InternalTripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<Map<String, UUID>> submitTrip(@RequestBody InternalTripRequest request) {
        UUID id = tripService.saveTrip(request);
        return ResponseEntity.ok(Map.of("id", id));
    }
}
