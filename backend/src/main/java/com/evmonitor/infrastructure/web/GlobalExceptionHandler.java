package com.evmonitor.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
