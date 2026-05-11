package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.persistence.xpeng.XpengImportJob;
import com.evmonitor.application.imports.xpeng.XpengImportService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/api/imports/xpeng")
@Slf4j
@RequiredArgsConstructor
public class XpengImportController {

    private final XpengImportService importService;

    /** 5 uploads / hour / user. Prevents brute-force on decryption password + tempdir abuse. */
    private final ConcurrentMap<UUID, Bucket> rateLimits = new ConcurrentHashMap<>();

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestParam("carId") UUID carId,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "password", required = false) String password,
                                    HttpServletRequest http) {
        UUID userId = principal.getUser().getId();
        if (!rateLimitFor(userId).tryConsume(1)) {
            return ResponseEntity.status(429).body(Map.of("error", "Zu viele Upload-Versuche. Bitte in einer Stunde erneut."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datei fehlt"));
        }
        try {
            XpengImportJob job = importService.uploadXlsx(userId, carId, file.getInputStream(),
                    password, WebUtils.clientIp(http), http.getHeader("User-Agent"));
            return ResponseEntity.accepted().body(toDto(job));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Xpeng upload failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload fehlgeschlagen: " + e.getMessage()));
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJob(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID jobId) {
        return importService.getJobForUser(jobId, principal.getUser().getId())
                .<ResponseEntity<?>>map(j -> ResponseEntity.ok(toDto(j)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobDto>> recentJobs(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(importService.recentJobsForUser(principal.getUser().getId()).stream()
                .map(this::toDto).toList());
    }

    private Bucket rateLimitFor(UUID userId) {
        return rateLimits.computeIfAbsent(userId, u -> Bucket.builder()
                .addLimit(limit -> limit.capacity(5).refillIntervally(5, Duration.ofHours(1)))
                .build());
    }

    private JobDto toDto(XpengImportJob j) {
        return new JobDto(
                j.getId(), j.getCarId(),
                j.getStatus().name(),
                j.getImportedTrips(), j.getImportedSessions(), j.getSkippedDuplicates(),
                j.getDataRangeStart(), j.getDataRangeEnd(),
                j.getErrorMessage(),
                j.getCreatedAt(), j.getStartedAt(), j.getCompletedAt());
    }

    public record JobDto(
            UUID id,
            UUID carId,
            String status,
            int importedTrips,
            int importedSessions,
            int skippedDuplicates,
            LocalDateTime dataRangeStart,
            LocalDateTime dataRangeEnd,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt) {}
}
