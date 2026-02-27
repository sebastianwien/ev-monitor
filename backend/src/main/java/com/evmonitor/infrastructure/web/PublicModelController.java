package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PublicModelService;
import com.evmonitor.application.PublicModelStatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public, unauthenticated API for car model statistics.
 * Used by public model pages (SEO) and the model index.
 * All data is aggregated and anonymized (no user-identifiable info).
 */
@RestController
@RequestMapping("/api/public")
public class PublicModelController {

    private final PublicModelService publicModelService;

    public PublicModelController(PublicModelService publicModelService) {
        this.publicModelService = publicModelService;
    }

    /**
     * GET /api/public/models/{brand}/{model}
     * Returns aggregated real-world statistics for a car model.
     * Excludes seed/test data. Returns 404 if model doesn't exist.
     */
    @GetMapping("/models/{brand}/{model}")
    public ResponseEntity<PublicModelStatsResponse> getModelStats(
            @PathVariable String brand,
            @PathVariable String model) {
        return publicModelService.getModelStats(brand, model)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/public/models
     * Returns all model names that have WLTP data (for sitemap / index).
     */
    @GetMapping("/models")
    public ResponseEntity<List<String>> getAllModelsWithWltpData() {
        return ResponseEntity.ok(publicModelService.getModelsWithWltpData());
    }
}
