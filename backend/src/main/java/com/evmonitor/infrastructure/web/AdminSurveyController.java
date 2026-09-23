package com.evmonitor.infrastructure.web;

import com.evmonitor.infrastructure.persistence.JpaSurveyResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Interne Umfrage-Auswertung. Liefert bewusst keine user_id, Auswertung ist anonym.
 */
@RestController
@RequestMapping("/api/admin/surveys")
@RequiredArgsConstructor
public class AdminSurveyController {

    private final JpaSurveyResponseRepository repo;

    public record SurveySummary(String slug, long responses) {}

    public record SurveyResponseRow(LocalDateTime createdAt, Map<String, Object> answers) {}

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SurveySummary>> list() {
        return ResponseEntity.ok(repo.countBySlug().stream()
                .map(c -> new SurveySummary(c.getSlug(), c.getResponses()))
                .toList());
    }

    @GetMapping("/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SurveyResponseRow>> responses(@PathVariable String slug) {
        return ResponseEntity.ok(repo.findBySurveySlugOrderByCreatedAtAsc(slug).stream()
                .map(r -> new SurveyResponseRow(r.getCreatedAt(), r.getAnswers()))
                .toList());
    }
}
