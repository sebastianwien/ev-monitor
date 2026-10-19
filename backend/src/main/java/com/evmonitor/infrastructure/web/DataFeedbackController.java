package com.evmonitor.infrastructure.web;

import com.evmonitor.application.feedback.DataFeedbackRequest;
import com.evmonitor.application.feedback.DataFeedbackResponse;
import com.evmonitor.application.feedback.DataFeedbackService;
import com.evmonitor.domain.feedback.FeedbackSubjectType;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** "Korrekt erkannt?" für automatisch erfasste Ladevorgänge (LOG) und Fahrten (TRIP). */
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class DataFeedbackController {

    private final DataFeedbackService feedbackService;

    /** Alle Bewertungen des Nutzers - wenige, deshalb ohne Paging; das Frontend ordnet sie den Karten zu. */
    @GetMapping
    public List<DataFeedbackResponse> mine(Authentication authentication) {
        return feedbackService.listForUser(principal(authentication).getUser().getId());
    }

    @PutMapping("/{type}/{subjectId}")
    public DataFeedbackResponse submit(@PathVariable FeedbackSubjectType type, @PathVariable UUID subjectId,
                                       @Valid @RequestBody DataFeedbackRequest request, Authentication authentication) {
        return feedbackService.submit(principal(authentication).getUser(), type, subjectId, request);
    }

    @DeleteMapping("/{type}/{subjectId}")
    public ResponseEntity<Void> clear(@PathVariable FeedbackSubjectType type, @PathVariable UUID subjectId,
                                      Authentication authentication) {
        feedbackService.clear(principal(authentication).getUser(), type, subjectId);
        return ResponseEntity.noContent().build();
    }

    private static UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
