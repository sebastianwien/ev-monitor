package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AdminChargingActivityRow;
import com.evmonitor.application.AdminUserGrowthRow;
import com.evmonitor.application.AdminUserRow;
import com.evmonitor.infrastructure.persistence.AdminQueryRepository;
import com.evmonitor.infrastructure.weather.TemperatureBackfillJob;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TemperatureBackfillJob temperatureBackfillJob;
    private final AdminQueryRepository adminQueryRepository;

    public AdminController(TemperatureBackfillJob temperatureBackfillJob,
                           AdminQueryRepository adminQueryRepository) {
        this.temperatureBackfillJob = temperatureBackfillJob;
        this.adminQueryRepository = adminQueryRepository;
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

    @GetMapping("/stats/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserRow>> getUserTable() {
        return ResponseEntity.ok(adminQueryRepository.getUserTable());
    }

    @GetMapping("/stats/user-growth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminUserGrowthRow>> getUserGrowth() {
        return ResponseEntity.ok(adminQueryRepository.getUserGrowth());
    }

    @GetMapping("/stats/charging-activity")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminChargingActivityRow>> getChargingActivity() {
        return ResponseEntity.ok(adminQueryRepository.getChargingActivity());
    }
}
