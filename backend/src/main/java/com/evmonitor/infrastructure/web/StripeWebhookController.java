package com.evmonitor.infrastructure.web;

import com.evmonitor.application.StripeService;
import com.stripe.exception.SignatureVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Handles Stripe webhook events.
 * Public endpoint — secured by Stripe signature verification, NOT by JWT.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeService stripeService;

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, String>> handleStripeWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (sigHeader == null || sigHeader.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing Stripe-Signature header"));
        }

        String payload;
        try {
            payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to read request body"));
        }

        try {
            stripeService.handleWebhookEvent(payload, sigHeader);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Stripe signature"));
        } catch (Exception e) {
            log.error("Stripe webhook processing failed - Stripe will retry", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal processing error"));
        }
    }
}
