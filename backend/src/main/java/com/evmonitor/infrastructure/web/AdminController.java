package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AdminChargingActivityRow;
import com.evmonitor.application.AdminUserGrowthRow;
import com.evmonitor.application.AdminUserRow;
import com.evmonitor.application.BatterySohService;
import com.evmonitor.application.PlausibleTrafficRow;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.external.PlausibleService;
import com.evmonitor.infrastructure.persistence.AdminQueryRepository;
import com.evmonitor.infrastructure.weather.TemperatureBackfillJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final TemperatureBackfillJob temperatureBackfillJob;
    private final AdminQueryRepository adminQueryRepository;
    private final PlausibleService plausibleService;
    private final BatterySohService batterySohService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    /**
     * Triggers one-time temperature backfill for all logs with geohash but no temperature.
     * Runs synchronously and returns a summary when done.
     */
    /**
     * Triggers SoH auto-detection for all cars with AT_VEHICLE logs but no SoH entry this year.
     * Safe to call multiple times - already-detected cars are skipped.
     */
    @PostMapping("/soh/redetect")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> redetectSoh() {
        int count = batterySohService.redetectForCarsWithoutSohThisYear();
        return ResponseEntity.ok("SoH detected for " + count + " cars");
    }

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

    @PostMapping("/emails/autosync-announcement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> sendAutoSyncAnnouncement(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        List<User> recipients = userRepository.findUsersForAutoSyncAnnouncement();
        if (dryRun) {
            return ResponseEntity.ok("Dry run: %d recipient(s) would receive the AutoSync announcement".formatted(recipients.size()));
        }
        int sent = 0;
        for (User user : recipients) {
            try {
                emailService.sendAutoSyncAnnouncementEmail(user.getEmail(), user.getRegistrationLocale());
                log.info("AutoSync announcement sent to user {}", user.getId());
                sent++;
            } catch (Exception e) {
                log.error("Failed to send AutoSync announcement to user {}", user.getId(), e);
            }
        }
        return ResponseEntity.ok("AutoSync announcement sent to %d/%d recipient(s)".formatted(sent, recipients.size()));
    }

    @GetMapping("/stats/traffic")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PlausibleTrafficRow>> getTraffic(
            @RequestParam(defaultValue = "30d") String period) {
        return ResponseEntity.ok(plausibleService.getTimeseries(period));
    }
}
