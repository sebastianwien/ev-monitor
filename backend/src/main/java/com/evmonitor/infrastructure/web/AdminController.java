package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.weather.TemperatureBackfillJob;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TemperatureBackfillJob temperatureBackfillJob;
    private final EmailService emailService;

    public AdminController(TemperatureBackfillJob temperatureBackfillJob, EmailService emailService) {
        this.temperatureBackfillJob = temperatureBackfillJob;
        this.emailService = emailService;
    }

    /**
     * Triggers one-time temperature backfill for all logs with geohash but no temperature.
     * Runs synchronously and returns a summary when done.
     */
    /** TODO: remove after testing */
    @PostMapping("/test-re-engagement-email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> testReEngagementEmail(@RequestParam String email, @RequestParam String username) {
        emailService.sendReEngagementEmail(email, username);
        return ResponseEntity.ok("Sent to " + email);
    }

    @PostMapping("/backfill-temperature")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> backfillTemperature() {
        String summary = temperatureBackfillJob.run();
        return ResponseEntity.ok(summary);
    }
}
