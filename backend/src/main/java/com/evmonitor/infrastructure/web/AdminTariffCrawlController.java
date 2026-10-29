package com.evmonitor.infrastructure.web;

import com.evmonitor.application.tariff.crawl.AdminTariffCrawlResponses;
import com.evmonitor.application.tariff.crawl.AdminTariffCrawlService;
import com.evmonitor.application.tariff.crawl.DriverPriceCheck;
import com.evmonitor.application.tariff.crawl.TariffCrawlProperties;
import com.evmonitor.application.tariff.crawl.TariffCrawlScheduler;
import com.evmonitor.application.tariff.crawl.TariffExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin-Tab "Ladekarten", Crawler-Teil: Quellen, offene Vorschläge, Lauf von Hand. */
@RestController
@RequestMapping("/api/admin/charging-tariffs/crawl")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@RequiredArgsConstructor
public class AdminTariffCrawlController {

    private final AdminTariffCrawlService service;
    private final TariffCrawlScheduler scheduler;
    private final TariffExtractor extractor;
    private final TariffCrawlProperties props;
    private final DriverPriceCheck driverCheck;

    @GetMapping
    public AdminTariffCrawlResponses.Overview overview() {
        return service.overview(extractor.isAvailable(), props.isEnabled(), props.isAutoApply());
    }

    /** Wo Fahrer mit einer Karte deutlich anders zahlen als der gepflegte Preis; nur Hinweise. */
    @GetMapping("/driver-check")
    public List<DriverPriceCheck.Deviation> driverCheck() {
        return driverCheck.deviations();
    }

    /** 202 gestartet, 409 läuft schon. */
    @PostMapping("/run")
    public ResponseEntity<Void> run() {
        return ResponseEntity.status(scheduler.tryStartManual() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).build();
    }

    @PostMapping("/proposals/{id}/apply")
    public AdminTariffCrawlResponses.Applied apply(@PathVariable UUID id) {
        return service.apply(id);
    }

    @PostMapping("/proposals/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id) {
        service.reject(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sources")
    public AdminTariffCrawlResponses.SourceRow create(@Valid @RequestBody AdminTariffCrawlResponses.SourceCommand cmd) {
        return service.create(cmd);
    }

    @PutMapping("/sources/{id}")
    public AdminTariffCrawlResponses.SourceRow update(@PathVariable UUID id, @Valid @RequestBody AdminTariffCrawlResponses.SourceCommand cmd) {
        return service.update(id, cmd);
    }
}
