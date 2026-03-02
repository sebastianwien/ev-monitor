package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.email.AlertEmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/errors")
public class FrontendErrorController {

    private final AlertEmailService alertEmailService;

    public FrontendErrorController(AlertEmailService alertEmailService) {
        this.alertEmailService = alertEmailService;
    }

    @PostMapping("/frontend")
    public ResponseEntity<Void> reportError(@RequestBody @Valid FrontendErrorRequest request) {
        // Rate-limit key: first 80 chars of the error message
        String errorKey = "fe_" + request.message().substring(0, Math.min(80, request.message().length()));
        String subject = "🌐 [EV Monitor FE] " + request.message().substring(0, Math.min(100, request.message().length()));
        String body = buildAlertBody(request);
        alertEmailService.sendAlert(errorKey, subject, body);
        return ResponseEntity.ok().build();
    }

    private String buildAlertBody(FrontendErrorRequest request) {
        return """
                EV Monitor – Frontend Error Alert
                ==================================
                Time:    %s
                URL:     %s
                Info:    %s
                Message: %s

                Stack:
                %s
                """.formatted(
                Instant.now(),
                request.url() != null ? request.url() : "unknown",
                request.info() != null ? request.info() : "unknown",
                request.message(),
                request.stack() != null ? request.stack() : "(no stack)"
        );
    }
}
