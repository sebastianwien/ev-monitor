package com.evmonitor.infrastructure.web;

import com.evmonitor.application.seo.SeoCompetition;
import com.evmonitor.application.seo.SeoCompetitionScheduler;
import com.evmonitor.application.seo.SeoCompetitionService;
import com.evmonitor.application.seo.SeoInsightsService;
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

import java.util.List;
import java.util.Map;

/** Unter-Tab "Wettbewerb": Top 10 bei Google, Wettbewerber, Suchvolumen und Begriffsideen. Fremde Titel sind Text. */
@RestController
@RequestMapping("/api/admin/seo/competition")
@RequiredArgsConstructor
public class AdminSeoCompetitionController {

    private final SeoCompetitionService service;
    private final SeoCompetitionScheduler scheduler;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SeoCompetition.View view(@RequestParam(defaultValue = "28") int days) {
        if (!SeoInsightsService.ALLOWED_PERIODS.contains(days)) throw new IllegalArgumentException("Zeitraum muss 7, 28 oder 90 Tage sein");
        return service.view(days);
    }

    @GetMapping("/keywords")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SeoCompetition.KeywordRow> keywords() {
        return service.keywordMetrics();
    }

    /** Holt im Hintergrund, was fällig ist. 409 ohne Zugang oder bei laufendem Abruf. */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Boolean>> run() {
        boolean started = scheduler.tryStartManual();
        return ResponseEntity.status(started ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).body(Map.of("started", started));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
