package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ApiSessionResponse;
import com.evmonitor.application.publicapi.ApiSessionsPageResponse;
import com.evmonitor.application.publicapi.ImportApiResult;
import com.evmonitor.application.publicapi.MergeSessionRequest;
import com.evmonitor.application.publicapi.PatchSessionRequest;
import com.evmonitor.application.publicapi.PublicApiImportService;
import com.evmonitor.application.publicapi.PublicApiSessionRequest;
import com.evmonitor.domain.ApiKey;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Charging Sessions", description = "Upload and manage charging sessions (Wallboxen, Skripte, Home-Automation)")
@RequiredArgsConstructor
public class PublicApiImportController {

    private static final int MAX_SESSIONS_PER_REQUEST = 100;

    private final PublicApiImportService importService;
    private final RateLimitService rateLimitService;

    @GetMapping("/sessions")
    @Operation(
            summary = "List charging sessions",
            description = """
                    Returns a paginated list of charging sessions for the authenticated user.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`

                    **Filtering:** Optionally filter by `car_id`, `from` date and `to` date (ISO format: `yyyy-MM-dd`).
                    If `car_id` is omitted, sessions across all of the user's cars are returned.

                    **Pagination:** Use `page` (0-based, default 0) and `size` (default 20, max 100).

                    **Sorting:** Newest sessions first.
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> listSessions(
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
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Maximale Seitengröße ist 100."));
        }
        if (page < 0 || size < 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ungültige Paginierungsparameter."));
        }

        LocalDateTime fromDt = parseDate(from, false);
        LocalDateTime toDt = parseDate(to, true);

        if ((from != null && fromDt == null) || (to != null && toDt == null)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ungültiges Datumsformat. Erwartet: yyyy-MM-dd"));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            ApiSessionsPageResponse result = importService.getSessions(
                    principal.getUser().getId(), carId, fromDt, toDt, page, size);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private LocalDateTime parseDate(String raw, boolean endOfDay) {
        if (raw == null) return null;
        try {
            LocalDate date = LocalDate.parse(raw);
            return endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("/sessions")
    @Operation(
            summary = "Upload charging sessions",
            description = """
                    Uploads one or more charging sessions for a specific car.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`

                    **Tier 1 (minimal, e.g. Wallbox):** only `date` and `kwh` required.
                    **Tier 2 (full):** add `odometer_km` + `soc_after` to enable consumption calculation.
                    **Tier 3 (charging provider):** add `is_public_charging: true` and `cpo_name` to track where you charged.
                    Use `GET /api/v1/charging-providers` for the canonical list of CPO names.

                    **Response:** returns `imported`, `skipped`, `errors` counts and `ids` (UUIDs of created sessions).
                    Use the IDs to update sessions later via `PATCH /api/v1/sessions/{id}`.

                    **Deduplication:** Sessions with the same timestamp are skipped.

                    **Rate limit:** 60 requests/hour per API key. Max 100 sessions per request.
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> uploadSessions(
            @Valid @RequestBody PublicApiSessionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        // Rate limiting per API Key — keyId wird vom ApiKeyAuthFilter gesetzt.
        // Fehlt es (z.B. JWT-Auth statt API Key), Request ablehnen: dieser Endpoint ist nur für API Keys.
        String keyId = (String) httpRequest.getAttribute("apiKeyId");
        if (keyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Dieser Endpoint erfordert einen API Key (evm_...)."));
        }
        if (!rateLimitService.tryConsumeApiUpload(keyId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit überschritten. Max. 60 Requests pro Stunde."));
        }

        // Batch size limit
        if (request.sessions().size() > MAX_SESSIONS_PER_REQUEST) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Maximal " + MAX_SESSIONS_PER_REQUEST + " Sessions pro Request erlaubt."));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        ApiKey apiKey = (ApiKey) httpRequest.getAttribute("apiKey");

        try {
            ImportApiResult result = importService.importSessions(principal.getUser().getId(), request, apiKey);
            return ResponseEntity.ok(Map.of(
                    "imported", result.imported(),
                    "skipped", result.skipped(),
                    "errors", result.errors(),
                    "results", result.results()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            // Race condition: duplicate session slipped past the isDuplicate check
            // Return gracefully instead of 500
            return ResponseEntity.ok(Map.of("imported", 0, "skipped", 1, "errors", 0));
        }
    }

    @GetMapping("/sessions/{id}")
    @Operation(
            summary = "Get a charging session",
            description = """
                    Returns a single charging session previously imported via the Public API.

                    **Restrictions:** Only sessions with `data_source = API_UPLOAD` can be retrieved via this endpoint.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> getSession(
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
            ApiSessionResponse session = importService.getSession(principal.getUser().getId(), id);
            return ResponseEntity.ok(session);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/sessions/{id}")
    @Operation(
            summary = "Update a charging session",
            description = """
                    Partially updates an existing charging session previously imported via the Public API.

                    Only fields included in the request body are updated — omitted fields keep their existing value.

                    **Restrictions:** Only sessions with `data_source = API_UPLOAD` can be updated via this endpoint.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> patchSession(
            @PathVariable UUID id,
            @Valid @RequestBody PatchSessionRequest patch,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String keyId = (String) httpRequest.getAttribute("apiKeyId");
        if (keyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Dieser Endpoint erfordert einen API Key (evm_...)."));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            importService.patchApiSession(principal.getUser().getId(), id, patch);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(
            summary = "Delete a charging session",
            description = """
                    Deletes a charging session owned by the authenticated user.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> deleteSession(
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
            importService.deleteApiSession(principal.getUser().getId(), id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sessions/{id}/merge")
    @Operation(
            summary = "Merge two charging sessions into one",
            description = """
                    Merges two charging sessions of the **same car** into a single session.

                    **Why this exists:** the same charge often arrives twice, from two sources that each
                    know only half the truth. A wallbox reports how much energy went *into the cable*
                    (`kwh`, measured at the charger), while the car reports how much arrived *in the battery*
                    (`kwh_at_vehicle`) plus the state of charge before and after. Merging the two yields one
                    session that carries both sides - which is what makes charging-loss and consumption
                    calculations accurate.

                    **Direction:** the session in the path (`{id}`) is the **target** and survives.
                    The session named in `source_session_id` is merged into it and is then **permanently
                    deleted**. This cannot be undone, so pick the target deliberately - its `id` is the one
                    your integration should keep referencing afterwards.

                    **How fields are combined:** every field that is empty on the target is filled from the
                    source (energy at charger and at vehicle, SoC before/after, odometer, duration, cost,
                    location). If a field is set on *both* sides, `prefer_source` decides who wins:
                    `false` (default) keeps the target's value, `true` takes the source's. The
                    `measurement_type` is derived automatically: once both energy values are present, the
                    session counts as `AT_CHARGER`, because `kwh` (the charger reading) is the leading value.

                    **Time window:** both sessions must lie within **24 hours** of each other. Anything
                    further apart is treated as two genuinely separate charges and rejected with `409
                    MERGE_WINDOW_EXCEEDED`. The window is generous on purpose: a slow AC charge can easily
                    run for 14 hours, and its two records may be timestamped at either end of it.

                    **Authentication:** `Authorization: Bearer evm_<your-api-key>`

                    **Returns** the merged session (`200`). Errors carry a machine-readable `code`:
                    `404` if either session does not exist, `403` if one of them belongs to another user,
                    `409` with `MERGE_DIFFERENT_CARS`, `MERGE_SELF` or `MERGE_WINDOW_EXCEEDED` if the two
                    sessions may not be combined.

                    **Rate limit:** 60 requests/hour per API key.
                    """,
            security = @SecurityRequirement(name = "ApiKey")
    )
    public ResponseEntity<?> mergeSession(
            @PathVariable UUID id,
            @Valid @RequestBody MergeSessionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String keyId = (String) httpRequest.getAttribute("apiKeyId");
        if (keyId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Dieser Endpoint erfordert einen API Key (evm_...)."));
        }
        if (!rateLimitService.tryConsumeApiUpload(keyId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit überschritten. Max. 60 Requests pro Stunde."));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        // 404/403/409 kommen als DomainException aus EvLogService und werden vom
        // GlobalExceptionHandler gemappt - hier bewusst kein try/catch.
        ApiSessionResponse merged = importService.mergeApiSessions(
                principal.getUser().getId(), id, request.sourceSessionId(), request.preferSourceOrDefault());
        return ResponseEntity.ok(merged);
    }
}
