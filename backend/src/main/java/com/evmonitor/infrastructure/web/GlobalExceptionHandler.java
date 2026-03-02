package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.email.AlertEmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AlertEmailService alertEmailService;

    public GlobalExceptionHandler(AlertEmailService alertEmailService) {
        this.alertEmailService = alertEmailService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        String code = ex.getMessage();
        HttpStatus status = switch (code) {
            case "TOKEN_EXPIRED" -> HttpStatus.GONE;
            case "INVALID_TOKEN" -> HttpStatus.BAD_REQUEST;
            case "EMAIL_NOT_VERIFIED" -> HttpStatus.FORBIDDEN;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("code", code, "message", friendlyMessage(code)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("code", "FORBIDDEN", "message", "Zugriff verweigert."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);

        String errorKey = ex.getClass().getName();
        String subject = "🚨 [EV Monitor BE] " + ex.getClass().getSimpleName();
        String body = buildAlertBody(ex, request);
        alertEmailService.sendAlert(errorKey, subject, body);

        return ResponseEntity.status(500).body(Map.of(
                "code", "INTERNAL_ERROR",
                "message", "Ein interner Fehler ist aufgetreten."
        ));
    }

    private String buildAlertBody(Exception ex, HttpServletRequest request) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        if (stackTrace.length() > 3000) {
            stackTrace = stackTrace.substring(0, 3000) + "\n... (truncated)";
        }

        return """
                EV Monitor – Backend Error Alert
                =================================
                Time:      %s
                Request:   %s %s
                Exception: %s
                Message:   %s

                Stacktrace:
                %s
                """.formatted(
                Instant.now(),
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getName(),
                ex.getMessage(),
                stackTrace
        );
    }

    private String friendlyMessage(String code) {
        return switch (code) {
            case "TOKEN_EXPIRED" -> "Der Bestätigungs-Link ist abgelaufen. Bitte fordere einen neuen an.";
            case "INVALID_TOKEN" -> "Ungültiger Bestätigungs-Link.";
            case "EMAIL_NOT_VERIFIED" -> "Bitte bestätige zuerst deine E-Mail-Adresse.";
            case "RATE_LIMITED" -> "Bitte warte kurz, bevor du erneut eine E-Mail anforderst.";
            case "Email is already in use." -> "Diese E-Mail-Adresse ist bereits registriert.";
            case "Username is already taken." -> "Dieser Username ist bereits vergeben.";
            default -> code;
        };
    }
}
