package com.evmonitor.infrastructure.web;

import com.evmonitor.application.LiveEligibilityService;
import com.evmonitor.application.PremiumProperties;
import com.evmonitor.application.StripeService;
import com.evmonitor.domain.SubscriptionTier;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final StripeService stripeService;
    private final UserRepository userRepository;
    private final PremiumProperties premiumProperties;
    private final LiveEligibilityService liveEligibilityService;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    public SubscriptionController(StripeService stripeService,
                                   UserRepository userRepository,
                                   PremiumProperties premiumProperties,
                                   LiveEligibilityService liveEligibilityService) {
        this.stripeService = stripeService;
        this.userRepository = userRepository;
        this.premiumProperties = premiumProperties;
        this.liveEligibilityService = liveEligibilityService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        boolean isAdmin = ROLE_ADMIN.equals(principal.getUser().getRole());
        var response = new java.util.HashMap<String, Object>();
        response.put("isPremium", user.isPremium());
        response.put("tier", user.getSubscriptionTier().name());
        response.put("premiumEnabled", premiumProperties.isEnabled() || isAdmin);
        response.put("subscriptionPeriodEnd", user.getSubscriptionPeriodEnd() != null
                ? user.getSubscriptionPeriodEnd().toString()
                : null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> createCheckout(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CheckoutRequest request) {

        boolean isAdmin = ROLE_ADMIN.equals(principal.getUser().getRole());
        if (!premiumProperties.isEnabled() && !isAdmin) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Premium not yet available"));
        }

        SubscriptionTier tier = parseTier(request.tier());

        // Server-side eligibility: Live-tier requires a Tesla connection. Frontend hides
        // the Live block for non-Tesla users, but a direct API call would otherwise sail
        // through Stripe and we'd take money for a feature the user cannot consume.
        if (tier == SubscriptionTier.AUTOSYNC_LIVE
                && !liveEligibilityService.isEligibleForLive(principal.getUser().getId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Tesla connection required for AutoSync Live",
                            "errorCode", "tesla_required"));
        }

        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        try {
            String successUrl = appBaseUrl + "/upgrade/success";
            String cancelUrl = appBaseUrl + "/upgrade/cancel";
            String checkoutUrl = stripeService.createCheckoutSession(user, request.plan(), tier, successUrl, cancelUrl);
            return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create checkout session"));
        }
    }

    private static SubscriptionTier parseTier(String raw) {
        if (raw == null || raw.isBlank()) return SubscriptionTier.AUTOSYNC;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "autosync_live", "live" -> SubscriptionTier.AUTOSYNC_LIVE;
            default -> SubscriptionTier.AUTOSYNC;
        };
    }

    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, Object>> upgradeToLive(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (!liveEligibilityService.isEligibleForLive(principal.getUser().getId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Tesla connection required for AutoSync Live",
                            "errorCode", "tesla_required"));
        }

        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        try {
            boolean updated = stripeService.upgradeToLive(user);
            if (!updated) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "No active subscription to upgrade",
                                "errorCode", "no_active_subscription"));
            }
            return ResponseEntity.ok(Map.of("status", "upgrade_requested"));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to upgrade subscription"));
        }
    }

    @PostMapping("/downgrade")
    public ResponseEntity<Map<String, Object>> downgradeToAutoSync(
            @AuthenticationPrincipal UserPrincipal principal) {

        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (user.getSubscriptionTier() != SubscriptionTier.AUTOSYNC_LIVE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Only Live subscribers can downgrade",
                            "errorCode", "not_live_subscriber"));
        }

        try {
            boolean updated = stripeService.downgradeToAutoSync(user);
            if (!updated) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "No active subscription to downgrade",
                                "errorCode", "no_active_subscription"));
            }
            return ResponseEntity.ok(Map.of("status", "downgrade_requested"));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to downgrade subscription"));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @AuthenticationPrincipal UserPrincipal principal) {

        User user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        try {
            boolean updated = stripeService.cancelAtPeriodEnd(user);
            if (!updated) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "No active subscription to cancel",
                                "errorCode", "no_active_subscription"));
            }
            return ResponseEntity.ok(Map.of("status", "cancel_requested"));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to cancel subscription"));
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

    public record CheckoutRequest(String plan, String tier) {
        public CheckoutRequest(String plan) { this(plan, null); }
    }
}
