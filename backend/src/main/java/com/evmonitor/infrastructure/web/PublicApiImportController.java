package com.evmonitor.infrastructure.web;

import com.evmonitor.application.publicapi.ImportApiResult;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Public API", description = "External upload API for charging sessions (Wallboxen, Skripte, Home-Automation)")
public class PublicApiImportController {

    private static final int MAX_SESSIONS_PER_REQUEST = 100;

    private final PublicApiImportService importService;
    private final RateLimitService rateLimitService;

    public PublicApiImportController(PublicApiImportService importService, RateLimitService rateLimitService) {
        this.importService = importService;
        this.rateLimitService = rateLimitService;
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
                    "errors", result.errors()
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
}
