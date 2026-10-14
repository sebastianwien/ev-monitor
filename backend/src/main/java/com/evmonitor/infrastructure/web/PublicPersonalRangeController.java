package com.evmonitor.infrastructure.web;

import com.evmonitor.application.personalrange.PersonalRangeRankingEntry;
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

import java.util.List;

/**
 * Public answers of the range calculator, no login, each limited per IP:
 * the range band on the model page before the gate (the full result with mail and stored profile stays
 * behind {@link PersonalRangeController}; the page degrades to the model band on 429), and the model
 * ranking for a profile, whose figures the list shows behind its own gate.
 */
@RestController
@RequestMapping("/api/public/personal-range")
@RequiredArgsConstructor
public class PublicPersonalRangeController {

    private final PersonalRangeService personalRangeService;
    private final RateLimitService rateLimitService;

    /**
     * The model ranking for a profile: the list's answers for every model from the model page calculation.
     * Public like the preview, the list shows the figures only behind its gate; nothing is stored.
     */
    @PostMapping("/ranking")
    public ResponseEntity<List<PersonalRangeRankingEntry>> ranking(@Valid @RequestBody PersonalRangeRequest request,
                                                                   HttpServletRequest httpRequest) {
        if (!rateLimitService.tryConsumeRanking(WebUtils.clientIp(httpRequest))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(personalRangeService.ranking(request));
    }

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
