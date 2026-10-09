package com.evmonitor.infrastructure.web;

import com.evmonitor.application.rating.PublicModelRatingsResponse;
import com.evmonitor.application.rating.PublicModelRatingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** Öffentliche Fahrerbewertungen einer Modellseite, ohne Login. */
@RestController
@RequiredArgsConstructor
public class PublicModelRatingsController {

    private static final CacheControl PUBLIC_10_MIN = CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic();

    private final PublicModelRatingsService ratingsService;

    @GetMapping("/api/public/models/{brand}/{model}/ratings")
    public ResponseEntity<PublicModelRatingsResponse> ratings(@PathVariable String brand, @PathVariable String model) {
        return ratingsService.forSlug(brand, model)
                .map(body -> ResponseEntity.ok().cacheControl(PUBLIC_10_MIN).body(body))
                .orElse(ResponseEntity.notFound().build());
    }
}
