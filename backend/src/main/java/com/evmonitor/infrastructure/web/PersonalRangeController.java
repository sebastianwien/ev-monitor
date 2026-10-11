package com.evmonitor.infrastructure.web;

import com.evmonitor.application.personalrange.PersonalRangeRequest;
import com.evmonitor.application.personalrange.PersonalRangeResponse;
import com.evmonitor.application.personalrange.PersonalRangeService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal range calculator on the model pages. Logged-in users only (falls under the
 * authenticated /api/** rule): the personal result is what the gate on the model page unlocks.
 */
@RestController
@RequestMapping("/api/personal-range")
@RequiredArgsConstructor
public class PersonalRangeController {

    private final PersonalRangeService personalRangeService;

    @PostMapping("/{brand}/{model}")
    public ResponseEntity<PersonalRangeResponse> calculate(@PathVariable String brand,
                                                           @PathVariable String model,
                                                           @Valid @RequestBody PersonalRangeRequest request,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return personalRangeService.calculate(principal.getUser().getId(), principal.getUser().isSeedData(),
                        brand, model, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/profile")
    public ResponseEntity<PersonalRangeRequest> profile(@AuthenticationPrincipal UserPrincipal principal) {
        return personalRangeService.getProfile(principal.getUser().getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
