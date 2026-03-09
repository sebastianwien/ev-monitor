package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.weather.TemperatureBackfillJob;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TemperatureBackfillJob temperatureBackfillJob;

    public AdminController(TemperatureBackfillJob temperatureBackfillJob) {
        this.temperatureBackfillJob = temperatureBackfillJob;
    }

    /**
     * Triggers one-time temperature backfill for all logs with geohash but no temperature.
     * Runs synchronously and returns a summary when done.
     */
    @PostMapping("/backfill-temperature")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> backfillTemperature() {
        String summary = temperatureBackfillJob.run();
        return ResponseEntity.ok(summary);
    }
}
