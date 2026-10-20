package com.evmonitor.infrastructure.web;

import com.evmonitor.application.seo.SeoAudit;
import com.evmonitor.application.seo.SeoAuditScheduler;
import com.evmonitor.application.seo.SeoAuditService;
import com.evmonitor.application.seo.SeoVitalsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Unter-Tab "Technik": On-Page-Befunde je Sitemap-URL. Befund-Texte enthalten Seiteninhalte, nie HTML. */
@RestController
@RequestMapping("/api/admin/seo/audit")
@RequiredArgsConstructor
public class AdminSeoAuditController {

    private final SeoAuditService auditService;
    private final SeoAuditScheduler scheduler;
    private final SeoVitalsService vitals;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SeoAudit.View audit() {
        return auditService.view(vitals.latest());
    }

    /** Startet einen Crawl im Hintergrund. 409, wenn keine Basis-URL konfiguriert ist oder ein Lauf aktiv ist. */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> run() {
        boolean started = scheduler.tryStartManual();
        return ResponseEntity.status(started ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).body(Map.of("started", started));
    }
}
