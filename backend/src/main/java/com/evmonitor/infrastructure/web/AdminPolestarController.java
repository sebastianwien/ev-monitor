package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AdminPolestarService;
import com.evmonitor.application.waitlist.WaitlistService;
import com.evmonitor.domain.WaitlistFeature;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Polestar-Testmodus im Admin: Verbindungen, Snapshots, Monatszähler (proxied vom connectors-service). */
@RestController
@RequestMapping("/api/admin/polestar")
@RequiredArgsConstructor
public class AdminPolestarController {

    private final AdminPolestarService service;
    private final WaitlistService waitlistService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> summary() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.summary());
    }

    /** Wer wartet auf einen Testplatz, in Reihenfolge des Eintrags; der Admin schaltet am Monatswechsel von Hand frei. */
    @GetMapping("/waitlist")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WaitlistService.AdminEntry> waitlist() {
        return waitlistService.adminEntries(WaitlistFeature.POLESTAR_DATA_PORTAL);
    }
}
