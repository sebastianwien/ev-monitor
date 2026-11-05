package com.evmonitor.infrastructure.web;

import com.evmonitor.application.seo.BotHitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * nginx spiegelt Bot-Abrufe der Prerender-Seiten hierher ({@code mirror}), geschützt über {@code X-Internal-Token}.
 * Der Pfad kommt als {@code X-Original-URI}, der Bot aus dem weitergereichten User-Agent.
 */
@RestController
@RequestMapping("/api/internal/seo")
@RequiredArgsConstructor
public class InternalSeoController {

    private final BotHitService bots;

    @PostMapping("/bot-hit")
    public ResponseEntity<Void> botHit(@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                       @RequestHeader(value = "X-Original-URI", required = false) String uri) {
        if (uri != null && uri.startsWith("/")) bots.record(userAgent, uri);
        return ResponseEntity.noContent().build();
    }
}
