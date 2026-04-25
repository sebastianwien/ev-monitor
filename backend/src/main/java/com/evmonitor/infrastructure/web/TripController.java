package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CreateTripRequest;
import com.evmonitor.application.EvTripResponse;
import com.evmonitor.application.MergeTripRequest;
import com.evmonitor.application.TripService;
import com.evmonitor.application.UpdateTripRequest;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    private boolean canAccessTrips(UserPrincipal principal) {
        return principal.getUser().isPremium()
                || "BETA_TESTER".equals(principal.getUser().getRole())
                || "ADMIN".equals(principal.getUser().getRole());
    }

    @PostMapping
    public ResponseEntity<?> createTrip(
            @Valid @RequestBody CreateTripRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (!canAccessTrips(principal)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            EvTripResponse response = tripService.createUserTrip(principal.getUser().getId(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateTrip(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTripRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (!canAccessTrips(principal)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            EvTripResponse response = tripService.updateTrip(id, principal.getUser().getId(), request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(
            @PathVariable UUID id,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (!canAccessTrips(principal)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            tripService.deleteTrip(id, principal.getUser().getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<?> mergeTrip(
            @PathVariable UUID id,
            @Valid @RequestBody MergeTripRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (!canAccessTrips(principal)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            EvTripResponse response = tripService.mergeTrips(id, request.mergeWithTripId(), principal.getUser().getId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<?> getTrips(
            @RequestParam UUID carId,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (!canAccessTrips(principal)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            List<EvTripResponse> trips = tripService.getTripsForCar(carId, principal.getUser().getId());
            return ResponseEntity.ok(trips);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
