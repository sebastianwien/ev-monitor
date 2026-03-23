package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PublicModelService;
import com.evmonitor.application.TopModelResponse;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public API for community-based EV rankings.
 * Accessible without authentication — JWT is optional (seed user demo mode).
 */
@RestController
@RequestMapping("/api/public/rankings")
public class PublicRankingsController {

    private final PublicModelService publicModelService;

    public PublicRankingsController(PublicModelService publicModelService) {
        this.publicModelService = publicModelService;
    }

    /**
     * GET /api/public/rankings/efficiency?limit=5
     * Returns the N most efficient EV models by real community consumption (lowest first).
     * Only models with >= 10 logs and a valid avg consumption are included.
     * Limit is capped at 10.
     */
    /**
     * GET /api/public/rankings/categories
     * Returns all vehicle categories with display names.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<PublicModelService.CategoryResponse>> getCategories() {
        return ResponseEntity.ok(publicModelService.getCategories());
    }

    @GetMapping("/efficiency")
    public ResponseEntity<List<TopModelResponse>> getMostEfficientModels(
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isSeedUser = principal != null && principal.getUser().isSeedData();
        return ResponseEntity.ok(publicModelService.getMostEfficientModels(Math.min(limit, 10), isSeedUser));
    }
}
