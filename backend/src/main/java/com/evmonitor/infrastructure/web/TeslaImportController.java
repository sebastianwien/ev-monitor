package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.DataSource;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages Tesla Fleet API import data in the core backend.
 *
 * DELETE /api/import/tesla/delete-all
 * Deletes all TESLA_FLEET_IMPORT and TESLA_LIVE logs for the authenticated user.
 */
@RestController
@RequestMapping("/api/import/tesla")
@Slf4j
public class TeslaImportController {

    private static final List<DataSource> TESLA_FLEET_SOURCES = List.of(DataSource.TESLA_FLEET_IMPORT, DataSource.TESLA_LIVE);

    private final EvLogRepository evLogRepository;

    public TeslaImportController(EvLogRepository evLogRepository) {
        this.evLogRepository = evLogRepository;
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
