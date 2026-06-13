package com.evmonitor.infrastructure.web;

import com.evmonitor.application.SaveTripStoryRequest;
import com.evmonitor.application.TripStoryResponse;
import com.evmonitor.application.TripStoryService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Owner-side Trip-Story management. All endpoints are owner-only; stories the user
 * does not own return 404 (not 403), consistent with {@link TripController}.
 * Public reads live in {@link PublicStoryController}.
 */
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class TripStoryController {

    private final TripStoryService tripStoryService;

    @GetMapping
    public ResponseEntity<List<TripStoryResponse>> getMyStories(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(tripStoryService.getMyStories(principal.getUser()));
    }

    @PostMapping
    public ResponseEntity<?> createStory(
            @Valid @RequestBody SaveTripStoryRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            TripStoryResponse response = tripStoryService.createStory(principal.getUser(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStory(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(tripStoryService.getStoryForEdit(id, principal.getUser()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStory(
            @PathVariable UUID id,
            @Valid @RequestBody SaveTripStoryRequest request,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(tripStoryService.updateStory(id, principal.getUser(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(tripStoryService.publish(id, principal.getUser()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublish(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            return ResponseEntity.ok(tripStoryService.unpublish(id, principal.getUser()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStory(@PathVariable UUID id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            tripStoryService.deleteStory(id, principal.getUser());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
