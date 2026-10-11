package com.evmonitor.infrastructure.web;

import com.evmonitor.application.promo.PromoCheck;
import com.evmonitor.application.promo.PromoCodeService;
import com.evmonitor.application.promo.PromoOffer;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Promo codes for the upgrade page and the teaser banner. Logged-in users only. */
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class PromoController {

    private static final int MAX_CODE_LENGTH = 40;

    private final PromoCodeService promoCodeService;
    private final UserRepository userRepository;

    /** May the logged-in user redeem this code? {valid, code, trialDays} or {valid: false, reason}. */
    @GetMapping("/promo/{code}")
    public ResponseEntity<Map<String, Object>> check(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable String code) {
        if (code.length() > MAX_CODE_LENGTH) return ResponseEntity.badRequest().build();
        PromoCheck check = promoCodeService.check(currentUser(principal), code);
        return ResponseEntity.ok(check.valid()
                ? Map.of("valid", true, "code", check.offer().code(), "trialDays", check.offer().trialDays())
                : Map.of("valid", false, "reason", check.reason().name()));
    }

    /** The code made for the user's sign-up source, for the teaser banner. 204 when there is none. */
    @GetMapping("/promo-offer")
    public ResponseEntity<PromoOffer> offer(@AuthenticationPrincipal UserPrincipal principal) {
        return promoCodeService.offerFor(currentUser(principal))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** Fresh from the database: utm source and trial state are not in the JWT. */
    private User currentUser(UserPrincipal principal) {
        return userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
