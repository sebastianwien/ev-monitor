package com.evmonitor.infrastructure.web;

import com.evmonitor.application.personalrange.PersonalRangePreviewResponse;
import com.evmonitor.application.personalrange.PersonalRangeRequest;
import com.evmonitor.application.personalrange.PersonalRangeService;
import com.evmonitor.infrastructure.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The range band on the model page before the gate: winter to summer range for the visitor's inputs,
 * no login. Only model aggregates that are public anyway leave the server; the full result stays
 * behind {@link PersonalRangeController}. Limited per IP, the page degrades to the model band on 429.
 */
@RestController
@RequestMapping("/api/public/personal-range")
@RequiredArgsConstructor
public class PublicPersonalRangeController {

    private final PersonalRangeService personalRangeService;
    private final RateLimitService rateLimitService;

    @PostMapping("/{brand}/{model}/preview")
    public ResponseEntity<PersonalRangePreviewResponse> preview(@PathVariable String brand,
                                                                @PathVariable String model,
                                                                @Valid @RequestBody PersonalRangeRequest request,
                                                                HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumePreview(WebUtils.clientIp(httpRequest))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return personalRangeService.preview(brand, model, request, false)
                .map(band -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(band))
                .orElse(ResponseEntity.notFound().build());
    }
}
