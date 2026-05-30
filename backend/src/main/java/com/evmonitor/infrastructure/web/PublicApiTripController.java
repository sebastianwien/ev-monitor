package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiTripResponse;
import com.evmonitor.application.publicapi.ApiTripsPageResponse;
import com.evmonitor.application.publicapi.PatchPublicTripRequest;
import com.evmonitor.application.publicapi.PublicApiTripRequest;
import com.evmonitor.application.publicapi.PublicApiTripService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@Tag(name = "Public API", description = "External upload API for charging sessions (Wallboxen, Skripte, Home-Automation)")
@RequiredArgsConstructor
public class PublicApiTripController {

    private final PublicApiTripService tripService;

    @GetMapping
    @Operation(
            summary = "List driving trips",
            description = """
                    Returns a paginated list of driving trips for the authenticated user.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`

                    **Filtering:** Optionally filter by `car_id`, `from` date and `to` date (ISO format: `yyyy-MM-dd`).
                    If `car_id` is omitted, trips across all of the user's cars are returned.

                    **Pagination:** Use `page` (0-based, default 0) and `size` (default 20, max 100).

                    **Sorting:** Newest trips first (by `started_at`).
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> listTrips(
            @RequestParam(name = "car_id", required = false) UUID carId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String keyId = (String) httpRequest.getAttribute("apiKeyId");
        if (keyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Dieser Endpoint erfordert einen API Key (evm_...)."));
        }
        if (size > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Maximale Seitengröße ist 100."));
        }
        if (page < 0 || size < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ungültige Paginierungsparameter."));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            ApiTripsPageResponse result = tripService.listTrips(
                    principal.getUser().getId(), carId, from, to, page, size);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    @Operation(
            summary = "Create a driving trip",
            description = """
                    Creates a new driving trip for a specific car.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`

                    The trip is stored with `data_source = API_UPLOAD`. Only API_UPLOAD trips can be
                    updated or deleted via this endpoint later.

                    **Rate limit:** 60 requests/hour per API key.
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> createTrip(
            @Valid @RequestBody PublicApiTripRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String keyId = (String) httpRequest.getAttribute("apiKeyId");
        if (keyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Dieser Endpoint erfordert einen API Key (evm_...)."));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            ApiTripResponse response = tripService.createTrip(principal.getUser().getId(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a driving trip",
            description = """
                    Returns a single driving trip by ID.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> getTrip(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String keyId = (String) httpRequest.getAttribute("apiKeyId");
        if (keyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Dieser Endpoint erfordert einen API Key (evm_...)."));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            ApiTripResponse response = tripService.getTrip(principal.getUser().getId(), id);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Update a driving trip",
            description = """
                    Partially updates a driving trip owned by the authenticated user.

                    Only fields included in the request body are updated - omitted fields keep their existing value.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> patchTrip(
            @PathVariable UUID id,
            @Valid @RequestBody PatchPublicTripRequest patch,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String keyId = (String) httpRequest.getAttribute("apiKeyId");
        if (keyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Dieser Endpoint erfordert einen API Key (evm_...)."));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            ApiTripResponse response = tripService.patchTrip(principal.getUser().getId(), id, patch);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a driving trip",
            description = """
                    Soft-deletes a driving trip owned by the authenticated user.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> deleteTrip(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String keyId = (String) httpRequest.getAttribute("apiKeyId");
        if (keyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Dieser Endpoint erfordert einen API Key (evm_...)."));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            tripService.deleteTrip(principal.getUser().getId(), id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
