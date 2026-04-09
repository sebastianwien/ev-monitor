package com.evmonitor.infrastructure.web;

import com.evmonitor.application.PremiumProperties;
import com.evmonitor.application.StripeService;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final StripeService stripeService;
    private final UserRepository userRepository;
    private final PremiumProperties premiumProperties;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    public SubscriptionController(StripeService stripeService,
                                   UserRepository userRepository,
                                   PremiumProperties premiumProperties) {
        this.stripeService = stripeService;
        this.userRepository = userRepository;
        this.premiumProperties = premiumProperties;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        boolean isAdmin = "ADMIN".equals(principal.getUser().getRole());
        return ResponseEntity.ok(Map.of(
                "isPremium", user.isPremium(),
                "premiumEnabled", premiumProperties.isEnabled() || isAdmin
        ));
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> createCheckout(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CheckoutRequest request) {

        boolean isAdmin = "ADMIN".equals(principal.getUser().getRole());
        if (!premiumProperties.isEnabled() && !isAdmin) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Premium not yet available"));
        }

        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        try {
            String successUrl = appBaseUrl + "/upgrade/success";
            String cancelUrl = appBaseUrl + "/upgrade/cancel";
            String checkoutUrl = stripeService.createCheckoutSession(user, request.plan(), successUrl, cancelUrl);
            return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create checkout session"));
        }
    }

    @PostMapping("/portal")
    public ResponseEntity<Map<String, Object>> createPortalSession(
            @AuthenticationPrincipal UserPrincipal principal) {

        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (user.getStripeCustomerId() == null || user.getStripeCustomerId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "No Stripe customer found"));
        }

        try {
            String returnUrl = appBaseUrl + "/settings";
            String portalUrl = stripeService.createPortalSession(user, returnUrl);
            return ResponseEntity.ok(Map.of("portalUrl", portalUrl));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create portal session"));
        }
    }

    public record CheckoutRequest(String plan) {}
}
