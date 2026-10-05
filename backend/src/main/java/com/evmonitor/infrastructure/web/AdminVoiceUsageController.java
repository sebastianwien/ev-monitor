package com.evmonitor.infrastructure.web;

import com.evmonitor.application.voice.VoiceUsageInsights;
import com.evmonitor.application.voice.VoiceUsageInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Mistral-Nutzung des Sprachlogs: Aufrufe, Tokens, Kosten, Fehler, Nutzer. Nur Metadaten. */
@RestController
@RequestMapping("/api/admin/voice-usage")
@RequiredArgsConstructor
public class AdminVoiceUsageController {

    private final VoiceUsageInsightsService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VoiceUsageInsights> insights(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.insights(days));
    }
}
