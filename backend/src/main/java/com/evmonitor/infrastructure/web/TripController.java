package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CreateTripRequest;
import com.evmonitor.application.EvTripResponse;
import com.evmonitor.application.MergeTripRequest;
import com.evmonitor.application.TripService;
import com.evmonitor.application.UpdateTripRequest;
import com.evmonitor.domain.exception.ForbiddenException;
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

    /**
     * Trip CRUD policy:
     *  - C (POST createTrip): only users for whom {@link com.evmonitor.domain.User#canCreateTripsManually()}
     *    is true (AUTOSYNC_LIVE tier or ADMIN/BETA_TESTER). Manuelles Anlegen ist Teil von
     *    AutoSync Live. The TripService re-checks the same gate as defense-in-depth.
     *  - R/U/M/D (GET/PATCH/POST-merge/DELETE): owner-only, plus data-source gate:
     *      * Live-detected trips (TESLA_LIVE, SMARTCAR_LIVE, TESLA_INFERRED) require
     *        {@link com.evmonitor.domain.User#canViewLiveTrips()} - same Tier-2/privileged gate.
     *      * Imported/manual trips (TESSIE, USER_CREATED, etc.) stay accessible to any
     *        owner regardless of subscription, because they're the user's own historical
     *        data. Tessie-import users without premium need this to manage their data.
     *    Trips that the user cannot see return 404 (not 403) to avoid leaking trip
     *    existence to non-entitled users.
     */
    @PostMapping
    public ResponseEntity<?> createTrip(
            @Valid @RequestBody CreateTripRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        if (!principal.getUser().canCreateTripsManually()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            EvTripResponse response = tripService.createUserTrip(principal.getUser(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ForbiddenException e) {
            // Service-level gate fires when the controller pre-check is somehow bypassed.
            // Defense in depth: surface as 403 (not 500) so the FE sees a consistent error code.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
        try {
            EvTripResponse response = tripService.updateTrip(id, principal.getUser(), request);
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
        try {
            tripService.deleteTrip(id, principal.getUser());
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
        try {
            EvTripResponse response = tripService.mergeTrips(id, request.mergeWithTripId(), principal.getUser());
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
        try {
            List<EvTripResponse> trips = tripService.getTripsForCar(carId, principal.getUser());
            return ResponseEntity.ok(trips);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
