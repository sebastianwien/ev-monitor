package com.evmonitor.infrastructure.web;

import com.evmonitor.application.InternalSmartcarWebhookLogRequest;
import com.evmonitor.application.SmartcarWebhookRawLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoint for persisting raw Smartcar webhook events (non-charging triggers).
 * Secured by InternalAuthFilter (X-Internal-Token header), NOT by user JWT.
 */
@RestController
@RequestMapping("/api/internal/smartcar")
@RequiredArgsConstructor
public class InternalSmartcarWebhookController {

    private final SmartcarWebhookRawLogService service;

    @PostMapping("/webhook-log")
    public ResponseEntity<Void> logWebhook(@RequestBody InternalSmartcarWebhookLogRequest request) {
        service.log(request);
        return ResponseEntity.ok().build();
    }
}
