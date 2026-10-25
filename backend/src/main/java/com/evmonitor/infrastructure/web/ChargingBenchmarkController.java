package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CpoPriceBenchmark;
import com.evmonitor.application.CpoPriceBenchmarkService;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Was andere Fahrer bei einem Betreiber zahlen - fuer den Story-Moment "zu teuer geladen".
 * Nur fuer angemeldete Nutzer (Security-Regel /api/**), nur Aggregate. 204 heisst: zu wenige
 * andere Fahrer oder kein bekannter Betreiber, das Frontend zeigt dann keinen Vergleich.
 */
@RestController
@RequestMapping("/api/charging-benchmarks")
@RequiredArgsConstructor
public class ChargingBenchmarkController {

    /** Laenge von charging_networks.name. */
    private static final int CPO_MAX = 100;

    private final CpoPriceBenchmarkService service;

    @GetMapping
    public ResponseEntity<CpoPriceBenchmark> benchmark(@RequestParam String cpo,
                                                       @RequestParam ChargingType type,
                                                       Authentication authentication) {
        if (cpo.isBlank() || cpo.length() > CPO_MAX || type == ChargingType.UNKNOWN) {
            return ResponseEntity.badRequest().build();
        }
        var requester = ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
        return service.benchmark(cpo, type, requester)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
