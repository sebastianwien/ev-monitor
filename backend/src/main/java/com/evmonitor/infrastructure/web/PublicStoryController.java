package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PublicTripStoryResponse;
import com.evmonitor.application.PublicTripStorySummaryResponse;
import com.evmonitor.application.TripStoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Public, unauthenticated reads of published Trip-Stories (under the permitAll'd
 * /api/public/** namespace). Serves only the stored story JSON - no joins back
 * into trip/log tables, so nothing beyond the published snapshot can leak.
 */
@RestController
@RequestMapping("/api/public/stories")
@RequiredArgsConstructor
public class PublicStoryController {

    private static final CacheControl PUBLIC_5M = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();

    private final TripStoryService tripStoryService;

    @GetMapping
    public ResponseEntity<List<PublicTripStorySummaryResponse>> getLatestStories() {
        return ResponseEntity.ok().cacheControl(PUBLIC_5M).body(tripStoryService.getLatestPublicStories());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PublicTripStoryResponse> getStory(@PathVariable String slug) {
        return tripStoryService.getPublicStory(slug)
                .map(story -> ResponseEntity.ok().cacheControl(PUBLIC_5M).body(story))
                .orElse(ResponseEntity.notFound().build());
    }
}
