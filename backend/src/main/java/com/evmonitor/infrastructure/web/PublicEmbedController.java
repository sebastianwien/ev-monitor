package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ModelTileResponse;
import com.evmonitor.application.PublicEmbedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Data for the embeddable model tile on partner sites (frontend/public/embed/ev-monitor-tile.js).
 * Readable from any origin without credentials (see SecurityConfig, /api/public/embed/**).
 * Unknown slugs answer 200 with hasData=false so the tile can render its fallback.
 */
@RestController
@RequestMapping("/api/public/embed")
@RequiredArgsConstructor
public class PublicEmbedController {

    private static final CacheControl PUBLIC_1H = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();
    private static final int MAX_SLUG_LENGTH = 60;

    private final PublicEmbedService publicEmbedService;

    @GetMapping("/model-tile")
    public ResponseEntity<ModelTileResponse> getModelTile(@RequestParam(required = false) String brand,
                                                          @RequestParam(required = false) String model) {
        ModelTileResponse tile = isSlug(brand) && isSlug(model)
                ? publicEmbedService.getModelTile(brand, model)
                : ModelTileResponse.unknown();
        return ResponseEntity.ok().cacheControl(PUBLIC_1H).body(tile);
    }

    private static boolean isSlug(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_SLUG_LENGTH;
    }
}
