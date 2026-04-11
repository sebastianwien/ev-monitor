package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PlatformStatsResponse;
import com.evmonitor.application.PublicBrandResponse;
import com.evmonitor.application.PublicModelService;
import com.evmonitor.application.PublicModelStatsResponse;
import com.evmonitor.application.TopModelResponse;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Public, unauthenticated API for car model statistics.
 * Used by public model pages (SEO) and the model index.
 * All data is aggregated and anonymized (no user-identifiable info).
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicModelController {

    private final PublicModelService publicModelService;

    private static final CacheControl PUBLIC_1H = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();
    private static final CacheControl NO_STORE = CacheControl.noStore();

    /**
     * GET /api/public/stats
     * Returns platform-wide stats: total supported models and real user count.
     */
    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsResponse> getPlatformStats() {
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_1H)
                .body(publicModelService.getPlatformStats());
    }

    /**
     * GET /api/public/brands/{brand}
     * Returns all models for a brand with WLTP and community data summary.
     * Returns 404 if brand doesn't exist.
     */
    @GetMapping("/brands/{brand}")
    public ResponseEntity<PublicBrandResponse> getBrandModels(
            @PathVariable String brand,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isSeedUser = principal != null && principal.getUser().isSeedData();
        CacheControl cc = isSeedUser ? NO_STORE : PUBLIC_1H;

        return publicModelService.getBrandModels(brand, isSeedUser)
                .map(body -> ResponseEntity.ok().cacheControl(cc).<PublicBrandResponse>body(body))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/public/models/{brand}/{model}
     * Returns aggregated real-world statistics for a car model.
     * Excludes seed/test data, UNLESS authenticated user is a seed user (then includes their own data).
     * Returns 404 if model doesn't exist.
     */
    @GetMapping("/models/{brand}/{model}")
    public ResponseEntity<PublicModelStatsResponse> getModelStats(
            @PathVariable String brand,
            @PathVariable String model,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID currentUserId = null;
        boolean isSeedUser = false;

        // Optional JWT: If authenticated, include seed data for seed users
        if (principal != null) {
            currentUserId = principal.getUser().getId();
            isSeedUser = principal.getUser().isSeedData();
        }

        CacheControl cc = isSeedUser ? NO_STORE : PUBLIC_1H;

        return publicModelService.getModelStats(brand, model, currentUserId, isSeedUser)
                .map(body -> ResponseEntity.ok().cacheControl(cc).<PublicModelStatsResponse>body(body))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/public/models/top?limit=N
     * Returns top N models by community log count with lightweight stats.
     * Used by landing page and model index — replaces N individual getModelStats calls.
     */
    @GetMapping("/models/top")
    public ResponseEntity<List<TopModelResponse>> getTopModels(
            @RequestParam(defaultValue = "12") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isSeedUser = principal != null && principal.getUser().isSeedData();
        CacheControl cc = isSeedUser ? NO_STORE : PUBLIC_1H;
        return ResponseEntity.ok().cacheControl(cc).body(publicModelService.getTopModels(Math.min(limit, 50), isSeedUser));
    }

    /**
     * GET /api/public/models
     * Returns all model names that have WLTP data and real community logs.
     * If authenticated as seed user, includes models with their own seed data.
     */
    @GetMapping("/models")
    public ResponseEntity<List<String>> getAllModelsWithWltpData(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID currentUserId = null;
        boolean isSeedUser = false;

        // Optional JWT: If authenticated, include seed data for seed users
        if (principal != null) {
            currentUserId = principal.getUser().getId();
            isSeedUser = principal.getUser().isSeedData();
        }

        CacheControl cc = isSeedUser ? NO_STORE : PUBLIC_1H;
        return ResponseEntity.ok().cacheControl(cc).body(publicModelService.getModelsWithWltpData(currentUserId, isSeedUser));
    }
}
