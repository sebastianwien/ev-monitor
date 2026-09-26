package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.EvTrip;
import com.evmonitor.domain.EvTripRepository;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Manages Tesla Fleet API import data in the core backend.
 *
 * GET /api/import/tesla/latest
 * Zeitpunkt der letzten Tesla-Ladung und -Fahrt, als Lebenszeichen der Sync-Strecke.
 *
 * DELETE /api/import/tesla/delete-all
 * Deletes all TESLA_FLEET_IMPORT and TESLA_LIVE logs for the authenticated user.
 */
@RestController
@RequestMapping("/api/import/tesla")
@Slf4j
@RequiredArgsConstructor
public class TeslaImportController {

    private static final List<DataSource> TESLA_FLEET_SOURCES = List.of(DataSource.TESLA_FLEET_IMPORT, DataSource.TESLA_LIVE);

    private static final List<String> TESLA_TRIP_SOURCES = List.of(EvTrip.DATA_SOURCE_TESLA_LIVE, EvTrip.DATA_SOURCE_TESLA_INFERRED);

    private final EvLogRepository evLogRepository;
    private final EvTripRepository evTripRepository;

    public record LatestTeslaImport(Instant lastChargeAt, Instant lastTripAt) {}

    @GetMapping("/latest")
    public LatestTeslaImport latestImport(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        // loggedAt liegt als UTC ohne Zone in der DB
        Instant lastChargeAt = evLogRepository.findLatestLoggedAtByUserIdAndDataSourceIn(userId, TESLA_FLEET_SOURCES)
                .map(t -> t.toInstant(ZoneOffset.UTC)).orElse(null);
        Instant lastTripAt = evTripRepository.findLatestTripEndedAtByUserIdAndDataSourceIn(userId, TESLA_TRIP_SOURCES)
                .map(OffsetDateTime::toInstant).orElse(null);
        return new LatestTeslaImport(lastChargeAt, lastTripAt);
    }

    @DeleteMapping("/delete-all")
    @Transactional
    public ResponseEntity<Void> deleteAllImports(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            evLogRepository.deleteAllByUserIdAndDataSourceIn(principal.getUser().getId(), TESLA_FLEET_SOURCES);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to delete Tesla imports for user {}", principal.getUser().getId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
