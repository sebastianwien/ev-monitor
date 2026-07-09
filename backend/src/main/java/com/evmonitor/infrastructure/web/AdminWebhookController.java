package com.evmonitor.infrastructure.web;

import com.evmonitor.application.AdminWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin webhook inspector: raw Smartcar webhook log, proxied from the connectors-service.
 */
@RestController
@RequestMapping("/api/admin/webhooks")
@RequiredArgsConstructor
public class AdminWebhookController {

    private final AdminWebhookService adminWebhookService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> getWebhookPage(@RequestParam String vehicleId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(adminWebhookService.getWebhookPage(vehicleId, page, size));
    }

    @GetMapping("/connections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminWebhookService.AdminConnection>> getConnections() {
        return ResponseEntity.ok(adminWebhookService.getConnections());
    }
}
