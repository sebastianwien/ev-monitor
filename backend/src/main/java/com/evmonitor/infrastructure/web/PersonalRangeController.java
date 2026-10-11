package com.evmonitor.infrastructure.web;

import com.evmonitor.application.personalrange.PersonalRangeRequest;
import com.evmonitor.application.personalrange.PersonalRangeResponse;
import com.evmonitor.application.personalrange.PersonalRangeService;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.security.RateLimitService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Personal range calculator on the model pages. Logged-in users only (falls under the
 * authenticated /api/** rule): the personal result is what the gate on the model page unlocks.
 */
@RestController
@RequestMapping("/api/personal-range")
@RequiredArgsConstructor
public class PersonalRangeController {

    private final PersonalRangeService personalRangeService;
    private final RateLimitService rateLimitService;

    /** @param email true also mails the result to the user (the gate promises that once; 3 a day at most) */
    @PostMapping("/{brand}/{model}")
    public ResponseEntity<PersonalRangeResponse> calculate(@PathVariable String brand,
                                                           @PathVariable String model,
                                                           @Valid @RequestBody PersonalRangeRequest request,
                                                           @RequestParam(defaultValue = "false") boolean email,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal.getUser().getId();
        return personalRangeService.calculate(userId, principal.getUser().isSeedData(), brand, model, request)
                .map(result -> {
                    if (email && rateLimitService.tryConsumeResultMail(userId)) {
                        personalRangeService.emailResult(userId, brand, model,
                                new EmailService.PersonalRangeMailContent(request, result));
                    }
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/profile")
    public ResponseEntity<PersonalRangeRequest> profile(@AuthenticationPrincipal UserPrincipal principal) {
        return personalRangeService.getProfile(principal.getUser().getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
