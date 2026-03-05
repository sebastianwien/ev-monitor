package com.evmonitor.infrastructure.web;

import com.evmonitor.application.StripeService;
import com.stripe.exception.SignatureVerificationException;
import jakarta.servlet.http.HttpServletRequest;
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
public class StripeWebhookController {

    private final StripeService stripeService;

    public StripeWebhookController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, String>> handleStripeWebhook(
            HttpServletRequest request,
            @RequestHeader("Stripe-Signature") String sigHeader) {

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
            // Return 200 for all other errors to prevent Stripe from retrying
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
    }
}
