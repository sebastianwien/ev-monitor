package com.evmonitor.infrastructure.web;

import com.evmonitor.application.seo.PlausibleSyncService;
import com.evmonitor.application.seo.SeoInsights;
import com.evmonitor.application.seo.SeoInsightsService;
import com.evmonitor.application.seo.SeoSyncScheduler;
import com.evmonitor.application.seo.SeoSyncService;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunEntity;
import com.evmonitor.infrastructure.persistence.seo.SeoSyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin-Tab "SEO & Traffic": Aggregate aus Search Console, Plausible und DB. Suchbegriffe sind fremder Text, nie HTML. */
@RestController
@RequestMapping("/api/admin/seo")
@RequiredArgsConstructor
public class AdminSeoController {

    private final SeoInsightsService insights;
    private final SeoSyncService syncService;
    private final SeoSyncScheduler scheduler;
    private final SeoSyncRunRepository runs;
    private final PlausibleSyncService plausibleSync;

    public record SyncRunDto(UUID id, String source, LocalDateTime startedAt, LocalDateTime finishedAt, String status,
                             int rowCount, LocalDate fromDay, LocalDate toDay, String error) {
        static SyncRunDto from(SeoSyncRunEntity e) {
            return new SyncRunDto(e.getId(), e.getSource(), e.getStartedAt(), e.getFinishedAt(), e.getStatus(),
                    e.getRowCount(), e.getFromDay(), e.getToDay(), e.getError());
        }
    }

    /** enabled/dataUntil: GSC; plausibleEnabled/plausibleDataUntil: Plausible (null ohne Daten). */
    public record StatusDto(boolean enabled, LocalDate dataUntil, boolean plausibleEnabled, LocalDate plausibleDataUntil,
                            List<SyncRunDto> runs) {
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public StatusDto status() {
        List<SyncRunDto> latest = runs.findTop10ByOrderByStartedAtDesc().stream().map(SyncRunDto::from).toList();
        return new StatusDto(syncService.isEnabled(), insights.dataUntil(), plausibleSync.isEnabled(),
                insights.plausibleDataUntil().orElse(null), latest);
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public SeoInsights.Overview overview(@RequestParam(defaultValue = "28") int days) {
        return insights.overview(days);
    }

    @GetMapping("/pages")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SeoInsights.PageRow> pages(@RequestParam(defaultValue = "28") int days,
                                           @RequestParam(defaultValue = "ALL") SeoInsights.PageFilter filter) {
        return insights.pages(days, filter);
    }

    @GetMapping("/queries")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SeoInsights.QueryRow> queries(@RequestParam(defaultValue = "28") int days,
                                              @RequestParam(defaultValue = "ALL") SeoInsights.BrandFilter brand,
                                              @RequestParam(defaultValue = "ALL") SeoInsights.QueryFilter filter) {
        return insights.queries(days, brand, filter);
    }

    @GetMapping("/campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SeoInsights.CampaignRow> campaigns(@RequestParam(defaultValue = "28") int days) {
        return insights.campaigns(days);
    }

    /** Startet einen Sync im Hintergrund. 409, wenn kein Zugang konfiguriert ist oder ein Lauf aktiv ist. */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> sync(@RequestParam(defaultValue = "30") int days) {
        boolean started = scheduler.tryStartManual(days);
        return ResponseEntity.status(started ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).body(Map.of("started", started));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
