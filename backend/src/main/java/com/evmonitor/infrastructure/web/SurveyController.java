package com.evmonitor.infrastructure.web;

import com.evmonitor.application.SurveyService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @GetMapping("/{slug}/status")
    public ResponseEntity<Map<String, Boolean>> getStatus(
            @PathVariable String slug,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        boolean responded = surveyService.hasResponded(slug, principal.getUser().getId());
        return ResponseEntity.ok(Map.of("responded", responded));
    }

    @PostMapping("/{slug}/respond")
    public ResponseEntity<Map<String, String>> respond(
            @PathVariable String slug,
            @RequestBody @NotEmpty Map<String, Object> answers,
            Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        boolean saved = surveyService.submit(slug, principal.getUser().getId(), answers);
        if (!saved) {
            return ResponseEntity.ok(Map.of("status", "already_responded"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
